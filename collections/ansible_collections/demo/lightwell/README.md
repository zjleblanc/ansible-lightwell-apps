# demo.lightwell

Ansible collection containing the roles used by the Lightwell Patch
Pipeline demo to build, deploy, verify, and roll back the
[`apps/java`](../../../../../apps/java) application on Podman.

See the top-level [README](../../../../../README.md) for the full demo
overview and pipeline flow.

## Roles

| Role | Purpose |
| --- | --- |
| [`demo.lightwell.build_app`](roles/build_app/README.md) | Build and push the application container image with Podman, authenticating to the Lightwell Network remediated repository. |
| [`demo.lightwell.deploy_app`](roles/deploy_app/README.md) | Deploy the container via Podman, recording the previously running image for rollback. |
| [`demo.lightwell.health_check`](roles/health_check/README.md) | Poll the application's `/healthz` endpoint with retries. |
| [`demo.lightwell.rollback`](roles/rollback/README.md) | Restore the previously running image when a deployment fails its health check. |
| [`demo.lightwell.report_status`](roles/report_status/README.md) | Post commit status back to GitHub via a GitHub App token, scoped per `app_environment`. |

## Usage

```yaml
- hosts: test
  roles:
    - role: demo.lightwell.deploy_app
    - role: demo.lightwell.health_check
```

## Local development

This collection lives inside the `ansible-lightwell` repository at
`collections/ansible_collections/demo/lightwell`, which `ansible.cfg`'s
`collections_path` setting picks up automatically -- no
`ansible-galaxy collection install` step is required to use it from this
repository's own playbooks.
