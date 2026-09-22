# Event-Driven Ansible rulebooks

`rulebooks/lightwell_webhook.yml` is the single entry point for GitHub
events in this pipeline. Rather than the AAP job template exposing its
own native webhook receiver, GitHub sends every `pull_request` and `push`
event to one AAP **Event Stream**, which forwards matching events into a
**Rulebook Activation** running this rulebook. The rulebook inspects the
payload and launches the matching job template:

| Event | Condition | Launches |
| --- | --- | --- |
| `pull_request` (opened/synchronize/reopened) | PR is present | `Lightwell // Build & Test` |
| `push` to `refs/heads/main` | ref is main, not a deletion | `Lightwell // Deploy Prod` |

The rulebook does not filter by changed file path -- see below.

### Path filtering

This rulebook does **not** filter events by changed file path. An earlier
version used a custom `demo.lightwell.path_filter` EDA event filter plugin
from this project's own collection, but Decision Environments don't mount
local collections -- only collections published to a reachable Galaxy/Hub
would be available to a Rulebook Activation's plugins. That made the
filter unusable outside of local testing.

Path filtering now happens entirely in
[`playbooks/deploy.yml`](../playbooks/deploy.yml): both the PR (dev) and
push (prod) code paths call the GitHub API to list changed files and exit
early via `meta: end_play` when nothing under `apps/java/` was touched.
See the root
[`README.md`](../README.md#path-based-filtering-only-deploy-when-app-files-change)
for details.

Full setup instructions (creating the Event Stream, its HMAC credential,
the Decision Environment, the EDA project, and the Rulebook Activation
that binds them all together) are in
[`docs/aap-setup.md`](../docs/aap-setup.md).

## Local testing

You can exercise the rulebook's conditions locally without AAP, using
[`ansible-rulebook`](https://ansible.readthedocs.io/projects/rulebook/en/stable/):

```bash
pip install ansible-rulebook
ansible-galaxy collection install ansible.eda

ansible-rulebook \
  --rulebook rulebooks/lightwell_webhook.yml \
  --inventory inventory/hosts.yml
```

With the rulebook running, POST a sample GitHub webhook payload to
`http://localhost:5000/endpoint` to see which rule fires. Note that
`run_job_template` still needs a reachable Controller and a configured
`Red Hat Ansible Automation Platform` credential to actually launch a job
-- for pure condition testing, swap the action for `debug` locally.
