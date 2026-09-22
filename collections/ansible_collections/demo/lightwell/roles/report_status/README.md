# report_status

Posts a commit status to GitHub via the [Statuses API](https://docs.github.com/en/rest/commits/statuses),
authenticating with a short-lived token minted from the Lightwell GitHub
App installation (see the `GitHub App Installation Access Token Lookup`
credential documented in `docs/aap-setup.md`).

This role exists because GitHub now sends `pull_request` and `push` events
to an EDA Event Stream rather than directly to an AAP job template's
native webhook receiver, so the automatic status-posting Controller would
otherwise provide is no longer available -- this role replaces it
explicitly.

## Required variables

| Variable | Description |
| --- | --- |
| `github_token` | Short-lived GitHub App installation access token, supplied as an extra var by the `Lightwell GitHub Status Reporter` credential attached to the job template. |
| `github_repo_full_name` | `<owner>/<repo>`, supplied by the EDA rulebook's `run_job_template` action from the webhook payload. |
| `app_git_sha` | The commit SHA to attach the status to (PR head SHA in test, merge commit SHA in prod). |
| `github_status_state` | One of `pending`, `success`, `failure`, `error`. |
| `github_status_description` | Short human-readable description shown in the GitHub UI. |

If any of `github_token`, `github_repo_full_name`, or `app_git_sha` are
missing (for example, when a job template is launched manually rather
than via the EDA rulebook), this role logs a warning and continues
without failing the play.

## Common variables (see `defaults/main.yml`)

| Variable | Default | Description |
| --- | --- | --- |
| `github_api_url` | `https://api.github.com` | Override for GitHub Enterprise Server. |
| `github_status_context` | `ci/lightwell-java-{{ app_environment }}` | The status "context" shown in GitHub -- also the name to select as a required status check in branch protection. |
