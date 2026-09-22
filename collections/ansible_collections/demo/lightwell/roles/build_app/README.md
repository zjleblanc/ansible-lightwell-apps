# demo.lightwell.build_app

Builds and pushes the Lightwell demo Java application container image
using rootless Podman on the demo host (inventory group `rhlw`), which
also hosts the `dev` and `prod` deployments. The application source is
synced from the controller to a temporary directory on that host via
`ansible.posix.synchronize`, since the host running the build is a
separate machine from wherever the playbook itself runs.

Authenticates to the Lightwell Network remediated repository at build
time by including `tasks/auth_java.yml`, which writes a `settings.xml`
`<server>` entry for Maven into the build context; it's removed after the
build, so credentials never reach the pushed image (the Containerfile's
builder stage that briefly holds it is discarded by the multi-stage
build).

## Required variables

| Variable | Description |
| --- | --- |
| `lightwell_username` | Lightwell Network service account username (`<id>\|<name>`). Supply via an AAP credential or Ansible Vault. |
| `lightwell_password` | Lightwell Network service account token. Supply via an AAP credential or Ansible Vault. |

## Common variables (see `defaults/main.yml`)

| Variable | Default | Description |
| --- | --- | --- |
| `app_source_dir` | `{{ playbook_dir }}/../apps/java` | Path to the checked-out application source. |
| `lightwell_registry_host` | `packages.redhat.com` | Lightwell Network host used in the generated credential file. |
| `app_image_registry` | `quay.io/zleblanc` | Registry/namespace the image is pushed to. |
| `app_image_name` | `lightwell-java-demo` | Image repository name. |
| `app_image_tag` | `{{ app_git_sha \| default('dev') }}` | Primary tag for the built image (typically the git commit SHA). |
| `app_image_push` | `true` | Whether to push the built image to the registry. |

`app_environment` (e.g. `dev` or `prod`) must be supplied by the caller;
it's used to compute the `<environment>-latest` convenience tag.

## Optional variables

| Variable | Description |
| --- | --- |
| `registry_auth_file` | Path, on the controller/execution environment, to a podman/docker `auth.json`-format file with credentials for pushing to a private registry (e.g. sourced from AAP's built-in **Container Registry** credential type). Its contents are copied to the build host, since that path isn't reachable from there directly. |

## Example

```yaml
- hosts: rhlw
  roles:
    - role: demo.lightwell.build_app
      vars:
        app_environment: dev
        app_image_tag: "{{ app_git_sha }}"
```
