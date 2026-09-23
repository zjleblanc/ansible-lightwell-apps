# Lightwell + Ansible Patch Pipeline Demo

A hands-on demo of a fully automated dependency patch pipeline for a
Java application.

**Red Hat & IBM [Lightwell Network](https://www.redhat.com/en/lightwell)**
supplies remediated (`.rhlw`-patched) packages, **Renovate** watches for
and proposes those patches as pull requests, and **Ansible Automation
Platform (AAP)** builds, tests, promotes, and -- if something goes wrong --
rolls back the application. GitHub events are routed through a single
**Event-Driven Ansible (EDA) Event Stream** rather than per-job-template
webhooks, and status is reported back to GitHub using a token minted from
a GitHub App installation instead of a static personal access token.

## Why this exists

Enterprises running long-lived, pinned versions of open source libraries
need a way to consume security patches without waiting on (or being
forced into) a disruptive major-version upgrade. Lightwell Network
delivers exactly that: backported, signed patches for the versions you
already run. This repo demonstrates how to wire that patch feed into a
real, auditable deployment pipeline instead of installing patches by hand.

## The demo application

This repository hosts a single demo application, a Java service under
[`apps/java/`](apps/java/README.md). It publishes to
`quay.io/lightwell-java-demo`.

## Architecture

```mermaid
flowchart TD
    classDef dev fill:#9ad8d8,stroke:#37a3a3,color:#004d4d
    classDef prod fill:#b6a6e9,stroke:#5e40be,color:#21134d
    classDef action fill:#92c5f9,stroke:#0066cc,color:#003366
    classDef transition fill:none,stroke:#fff4cc,stroke-dasharray: 1 3

    DetectPatch["🤖 Renovate Bot\ndetects new .rhlw patch"] --> GitHubPR

    subgraph DevStage [" "]
        GitHubPR["GitHub Pull Request"]
        GitHubPR -->|"Native webhook\npull_request event"| EventStreamPR["GitHub Event Stream"]
        EventStreamPR --> RulebookPR["Rulebook Activation\nrulebooks/lightwell_webhook.yml"]
        RulebookPR --> DeployDevPlaybook["AAP Job Template:\nLightwell // Build & Test\nplaybooks/deploy.yml"]
        DeployDevPlaybook --> PathFilter{"app files changed?"}
        PathFilter -->|"No"| Skip["Post Success\n(no-op)"]
        PathFilter -->|"Yes"| BuildImg["Build Container Image\n(Podman)"]
        BuildImg --> DeployTest["Deploy to Dev\n(Podman on RHEL)"]
        DeployTest --> HealthTest["Health Check\n(Dev Environment)"]
        HealthTest -->|"Pass"| ApprovePR["Update PR Check: Pass"]
        HealthTest -->|"Fail"| FailPR["Update PR Check: Fail"]
    end

    ApprovePR -.- DevToProd["‼️ Code Promotion"]
    DevToProd -.-> MergeMain["🧑 Merge to main"]

    subgraph ProdStage [" "]
        MergeMain -->|"Native webhook\npush event"| EventStreamPush["GitHub Event Stream"]
        EventStreamPush --> RulebookPush["Rulebook Activation\nrulebooks/lightwell_webhook.yml"]
        RulebookPush --> DeployProdPlaybook["AAP Job Template:\nLightwell // Deploy Prod\nplaybooks/deploy.yml"]
        DeployProdPlaybook --> BuildImgProd["Rebuild Container Image\nfrom merge commit (Podman)"]
        BuildImgProd --> DeployProd["Deploy to Prod"]
        DeployProd --> HealthProd["Health Check"]
        HealthProd -->|"Pass"| Done["Deployment Complete"]
        HealthProd -->|"Fail"| Rollback["Automated Rollback"]
    end

    style DevStage fill:none,stroke:#37a3a3,stroke-dasharray:5 5,stroke-width:2px
    style ProdStage fill:none,stroke:#5e40be,stroke-dasharray:5 5,stroke-width:2px

    class EventStreamPR,RulebookPR,DeployDevPlaybook,PathFilter,Skip,BuildImg,DeployTest,HealthTest,ApprovePR,FailPR dev
    class EventStreamPush,RulebookPush,DeployProdPlaybook,BuildImgProd,DeployProd,HealthProd,Done,Rollback prod
    class GitHubPR,MergeMain action
    class DevToProd transition
```

## Repository layout

```
ansible-lightwell/
├── apps/
│   └── java/               # Demo Java application -- see apps/java/README.md
│       ├── pom.xml         # Uses the Lightwell Remediated index as primary
│       ├── src/
│       └── Containerfile
├── playbooks/
│   ├── deploy.yml          # Unified build & deploy (build on dev, rollback on prod)
│   └── rollback.yml        # Standalone/manual rollback
├── collections/
│   ├── requirements.yml    # Third-party collections (containers.podman, ansible.eda, etc.)
│   └── ansible_collections/demo/lightwell/   # Our own demo.lightwell collection
│       ├── galaxy.yml
│       └── roles/
│           ├── build_app/     # Build & push the container image via Podman
│           ├── deploy_app/    # Deploy the container via a Podman Quadlet unit
│           ├── health_check/  # Poll /healthz with retries
│           ├── rollback/      # Restore the previous image
│           └── report_status/ # Post commit status back to GitHub via a GitHub App token
├── rulebooks/
│   ├── README.md            # How the rulebook routes GitHub events
│   └── lightwell_webhook.yml   # Routes PR/push events to job templates
├── inventory/               # Single "rhlw" host group and vars, including the dev/prod port map
├── renovate.json            # Renovate config for the Java (Maven) manifest
├── docs/aap-setup.md        # Full AAP configuration walkthrough
└── .pre-commit-config.yaml, .ansible-lint, .yamllint.yml, .gitleaks.toml
```

## The demo application

[`apps/java/`](apps/java/README.md) is a self-contained application with
its own README, dependency manifest, container build, and tests. Any
dependency version carrying the Lightwell `.rhlw-0000X` suffix is called
out visibly in the app, so a Renovate-driven version bump is easy to spot.

## Path-based filtering: only deploy when app files change

Not every commit or PR needs a build. A change to `README.md` or
`renovate.json` shouldn't burn CI minutes building and deploying the
application. Both the push (prod) and pull-request (dev) code paths are
filtered the same way, entirely inside
[`playbooks/deploy.yml`](playbooks/deploy.yml):

### Push events (production deploys)

The first `pre_task` block in the deploy play calls the GitHub
[Commits API](https://docs.github.com/en/rest/commits/commits#get-a-commit)
for `app_git_sha` (the pushed commit) to get its list of changed files, and
ends the play early -- posting a "Skipped" success status back to the
commit -- when nothing under `apps/java/` was modified.

### Pull-request events (dev builds)

GitHub `pull_request` webhook payloads don't include a list of changed
files (only an integer count), so this can't be checked from the event
payload alone. The first task block in the build play calls the GitHub
[Pull Request Files API](https://docs.github.com/en/rest/pulls/pulls#list-pull-requests-files)
to get the changed file list, and ends the play early (posting a "Skipped"
success status back to the PR) when nothing under `apps/java/` was
modified.

### Why filtering lives in the playbook, not the EDA rulebook

An earlier version filtered push events at the EDA layer with a custom
`demo.lightwell.path_filter` event filter plugin, so unwanted pushes never
launched an AAP job at all. That plugin lived in this project's own
collection -- but Decision Environments don't mount local collections, so
it was never actually available to the Rulebook Activation in a real AAP
deployment, only in local `ansible-rulebook` testing. It's been removed,
and [`rulebooks/lightwell_webhook.yml`](rulebooks/lightwell_webhook.yml)
no longer filters by path at all; every matching `pull_request`/`push`
event launches the job template, and that job template's playbook
decides whether to actually build/deploy.

### Alternatives considered

| Approach | Pros | Cons |
| --- | --- | --- |
| **EDA event filter plugin** (previous push approach) | Cheapest: no AAP job is launched at all. | Requires a custom collection plugin, which Decision Environments don't mount in practice; doesn't work for PR events either, since the payload lacks file paths. |
| **GitHub Actions `paths:` filter → `repository_dispatch`** | GitHub-native path filtering; bullet-proof. | Adds a second trigger layer and couples the pipeline to Actions. |
| **Early `meta: end_play` in the playbook** (current approach, both cases) | Works regardless of payload content; needs no plugin support from the Decision Environment; can post an explanatory status back to GitHub. | A job is still launched (albeit short-lived); the GitHub API call adds ~1 s. |

The chosen approach trades a small amount of AAP job overhead (one short
job per event) for a filtering mechanism that only depends on the
playbook's own GitHub API calls -- no reliance on plugins the Decision
Environment may not have.

## The patch pipeline, end to end

1. **Renovate** (configured in [`renovate.json`](renovate.json)) scans
   the app's dependency manifest (`apps/java/pom.xml`) against its
   Lightwell Remediated repository. When a new `.rhlw` patch is
   published, it opens a pull request bumping the pinned version.
2. The PR's `pull_request` webhook lands on a single **EDA Event Stream**,
   which forwards it to the `Lightwell Patch Pipeline Router` rulebook
   activation ([`rulebooks/lightwell_webhook.yml`](rulebooks/lightwell_webhook.yml)).
   The rulebook matches the `opened`/`synchronize`/`reopened` condition and
   launches **Lightwell // Build & Test**, which runs
   [`playbooks/deploy.yml`](playbooks/deploy.yml) with `app_environment: dev`:
   check whether the app's files actually changed (see
   [Path-based filtering](#path-based-filtering-only-deploy-when-app-files-change)
   above), and if so, build the image from the PR branch, deploy it to
   `dev` via a Podman Quadlet unit, and run a strict health check against
   `/healthz`.
3. The playbook's `demo.lightwell.report_status` role posts the result
   back to the PR as a GitHub commit status (context
   `ci/lightwell-java-dev`), authenticating with a token minted on
   demand from a GitHub App installation (via the
   `GitHub App Installation Access Token Lookup` credential) -- no static
   PAT is stored in AAP.
4. Branch protection on `main` requires the relevant check(s) to pass and
   requires at least one approving review before the PR can merge.
5. Merging to `main` sends a `push` webhook to the same Event Stream; the
   rulebook matches the `refs/heads/main` condition and launches
   **Lightwell // Deploy Prod**, which runs
   [`playbooks/deploy.yml`](playbooks/deploy.yml) with `app_environment: prod`:
   deploy the same tested image to `prod` and health-check it again.
6. If the prod health check fails, the playbook automatically invokes the
   `demo.lightwell.rollback` role, which restores the previously running
   image and re-verifies health -- no manual intervention required for
   the common case. Either way, `demo.lightwell.report_status` posts the
   final result back to the commit.

Full AAP resource setup (credentials, project, inventory, job templates,
the GitHub App, Event Stream, and rulebook activation) is documented step
by step in [`docs/aap-setup.md`](docs/aap-setup.md).

## Lightwell Network configuration

[`apps/java/pom.xml`](apps/java/pom.xml) sets the Lightwell Remediated
Maven repository as the primary repository, with Maven Central as a
fallback for any package it doesn't mirror.

Authentication uses a Lightwell Network service account (format
`<account-id>|<service-account-name>` plus a token). **These credentials
are never committed to this repository.** They are injected at build time
as a build-context Maven `settings.xml` via
`demo.lightwell.build_app`'s `auth_java.yml` task -- and supplied to
Renovate and AAP as secrets/credentials -- see
[`renovate.json`](renovate.json)'s `hostRules` and
[`docs/aap-setup.md`](docs/aap-setup.md#lightwell-network-service-account-custom-credential-type).

### Where the Lightwell service account credentials must live

The same username/token pair is needed in exactly three places, and
nowhere else:

| Location | Purpose | Never do this |
| --- | --- | --- |
| **AAP credential** of type `Lightwell Network` (custom credential type, [`docs/aap-setup.md`](docs/aap-setup.md#lightwell-network-service-account-custom-credential-type)) | Injected into the `build_app` role run as `lightwell_username` / `lightwell_password` extra vars, written to a short-lived credential file used only for the container build, then deleted. | Do not put these values in `group_vars`, role `defaults/`, or any extra-vars file checked into git. |
| **GitHub repository secrets** `LIGHTWELL_USERNAME` and `LIGHTWELL_TOKEN` | Referenced by [`renovate.json`](renovate.json)'s `hostRules` (`{{ secrets.LIGHTWELL_USERNAME }}` / `{{ secrets.LIGHTWELL_TOKEN }}`) so Renovate can query the Lightwell Remediated index for new patches. | Do not paste the raw values into `renovate.json` or any onboarding config committed to the repo. |
| **Local developer machine**, credential file only if resolving Lightwell-remediated packages locally (outside of a container build) | Lets your local package manager resolve `.rhlw` packages directly for local testing. | Do not commit your local credential file, and never copy it into the repo working directory (`.gitignore` already excludes any stray `settings.xml`). |

`.gitleaks.toml` includes custom rules that specifically detect the
Lightwell username format (`<id>|<name>`), Lightwell JWT tokens, and
credential blocks, so an accidental commit of any of the above is caught
by the pre-commit hook before it ever reaches git history.

## Setting up Renovate

The steps below are what's needed to bring Renovate up on a fresh copy of
this repo (e.g. after forking it into your own GitHub org).

### 1. Install the Renovate GitHub App

Renovate's hosted [GitHub App](https://github.com/apps/renovate) is free
for public and private repositories, with no usage limits.

- Go to [github.com/apps/renovate](https://github.com/apps/renovate) and
  click **Install**.
- Pick the account/org that owns the repo, then select **Only select
  repositories** and choose this one (or **All repositories** if you want
  it everywhere).
- No plan selection or payment step -- installing grants access
  immediately.

Renovate then reads the [`renovate.json`](renovate.json) already
committed at the repo root and starts scanning on its own schedule; no
onboarding PR is needed since the config file already exists.

### 2. Configure `renovate.json`

The committed [`renovate.json`](renovate.json) is ready to use as-is:

- `enabledManagers: ["maven"]` -- scans only the Java/Maven ecosystem.
- `additionalBranchPrefix: "{{parentDir}}-"` -- splits Renovate branches
  and PRs by the package manifest's parent directory.
- `packageRules` -- the `maven` manager is disabled by default, then
  re-enabled only for packages Lightwell actually remediates, scoped to
  `apps/java/**` via `matchFileNames`. See
  [`renovate-package-scoping.mdc`](.cursor/rules/renovate-package-scoping.mdc)
  for the exact pattern to follow.
- `hostRules` -- authenticates against `packages.redhat.com` using
  `{{ secrets.LIGHTWELL_USERNAME }}` / `{{ secrets.LIGHTWELL_TOKEN }}`
  (see step 3).
- `vulnerabilityAlerts.enabled: true` -- runs immediately on CVE
  detection, bypassing the `schedule` below.

### 3. Add the Lightwell credentials

`hostRules` references two secrets that must be defined in the
**Mend Developer Portal**, not as GitHub repository secrets (the hosted
Renovate app can't read GitHub Actions secrets):

- Go to [developer.mend.io](https://developer.mend.io/), find this
  repository, and add `LIGHTWELL_USERNAME` and `LIGHTWELL_TOKEN` as
  encrypted secrets there.
- See [Where the Lightwell service account credentials must
  live](#where-the-lightwell-service-account-credentials-must-live)
  above for what these values are and where else they're used.

### 4. Scan schedule

`renovate.json` sets:

```json
"timezone": "America/Chicago",
"schedule": ["before 7am every day"]
```

This limits scans (and new/updated PRs) to a daily window before 7 AM
Central. `vulnerabilityAlerts` ignores this window and fires as soon as a
CVE is published. To change the cadence, edit the `schedule` array using
[later.js syntax](https://breejs.github.io/later/), e.g.:

- `"before 7am on Monday"` -- weekly
- `"every weekday"` -- Monday-Friday, any time
- Remove the `schedule` key entirely -- scan at any time

### 5. Trigger a scan manually

Two ways to force a scan without waiting for the schedule:

- **Dependency Dashboard issue** (recommended): after the first scan,
  Renovate opens a "Dependency Dashboard" issue in the repo. Check the
  "Click on this checkbox to trigger a scan" box in that issue and save;
  Renovate picks up the change within a few minutes.
- **Rebase an existing PR**: on any open Renovate PR, tick the "rebase"
  checkbox in the PR description to force Renovate to re-evaluate that
  one dependency immediately.

### What happens next

Once Renovate opens a PR bumping a Lightwell-remediated package, it flows
through the same pipeline described in [The patch pipeline, end to
end](#the-patch-pipeline-end-to-end) above -- EDA routes the webhook to
AAP, which builds, tests, and reports status back to the PR.

## Code quality: linting and pre-commit hooks

This repo uses [pre-commit](https://pre-commit.com/) to enforce the same
checks locally that a real enterprise pipeline would run in CI:

| Tool | Purpose |
| --- | --- |
| [gitleaks](https://github.com/gitleaks/gitleaks) | Secret scanning, including custom rules for Lightwell service account tokens and credential blocks ([`.gitleaks.toml`](.gitleaks.toml)) |
| [ansible-lint](https://ansible.readthedocs.io/projects/lint/) | Enforces the `production` rule profile across all playbooks and roles ([`.ansible-lint`](.ansible-lint)) |
| [yamllint](https://yamllint.readthedocs.io/) | YAML style consistency ([`.yamllint.yml`](.yamllint.yml)) |

Set up once per clone:

```bash
pip install pre-commit
pre-commit install
```

Run against the whole repo at any time:

```bash
pre-commit run --all-files
```

## Prerequisites for a full live run

- A GitHub repository with webhooks enabled and branch protection
  configured on `main`.
- A GitHub App installed on the repository (commit-status write access)
  for AAP to authenticate as when posting status checks -- see
  [`docs/aap-setup.md`](docs/aap-setup.md#github-app-and-status-reporting-credentials).
- An AAP instance (2.5+) with Event-Driven Ansible enabled and reachable
  from GitHub -- see [`docs/aap-setup.md`](docs/aap-setup.md).
- A single Podman-capable RHEL host (inventory group `rhlw`) that AAP
  both builds the app's image on and deploys `dev`/`prod` to. Each
  environment runs as a separate container on its own port on that one
  host (see `app_port_map` in
  [`inventory/group_vars/all.yml`](inventory/group_vars/all.yml)) so they
  don't collide.
- A container registry that both AAP and the target host can reach,
  named by convention `lightwell-java-demo` (default:
  `quay.io/lightwell-java-demo`).
- A Lightwell Network service account.
