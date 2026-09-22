# demo.lightwell.deploy_app

Deploys the Lightwell demo Java application container via a Podman
Quadlet (`.container`) unit on the target host, so systemd manages the
container's lifecycle and it survives reboots. Before deploying, it
records the currently running image reference to
`app_previous_image_file` so the `rollback` role can restore it later.

## Required variables

| Variable | Description |
| --- | --- |
| `app_environment` | `dev` or `prod`; used for logging and health check behavior. |

## Common variables (see `defaults/main.yml`)

| Variable | Default | Description |
| --- | --- | --- |
| `app_port_map` | `{dev: 8082, prod: 8083}` | Host port lookup by `app_environment`, so `dev` and `prod` coexist on the same host. |
| `app_container_name` | `lightwell-java-demo` (`-dev` suffix for `dev`) | Name of the running container and the Quadlet unit. Suffixed with `-dev` for the dev environment so `dev` and `prod` can be deployed to, and coexist on, the same host. |
| `app_host_port` | `app_port_map[app_environment]` | Host port mapped to the container's port 8080. Derived from `app_environment` so `dev` and `prod` can run side-by-side on the same host without colliding. |
| `app_previous_image_file` | `/opt/lightwell-demo/java/{{ app_environment }}/previous_image.txt` | Where the previous image reference is persisted for rollback, namespaced by environment. |
| `quadlet_dir` | `/etc/containers/systemd` | Directory the Quadlet `.container` file is written to. Podman's systemd generator turns this into a `.service` unit on `daemon-reload`. |
| `app_service_name` | `{{ app_container_name }}` | Name of the systemd service generated from the Quadlet unit (`{{ app_service_name }}.service`). |
| `app_image_registry` | `quay.io/zleblanc` | Registry/namespace the image was pushed to by `build_app`. Mirrored here so this role doesn't depend on `group_vars` being applied. |
| `app_image_name` | `lightwell-java-demo` | Image repository name. |
| `app_image_tag` | `{{ app_git_sha \| default('dev') }}` | Tag to deploy (typically the git commit SHA built by `build_app`). |

## Optional variables

| Variable | Description |
| --- | --- |
| `registry_auth_file` | Path, on the controller/execution environment, to a podman/docker `auth.json`-format file with credentials for pulling from a private registry. Its contents are copied to the target host, since that path isn't reachable from there directly. |
