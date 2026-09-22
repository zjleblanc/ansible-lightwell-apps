# demo.lightwell.health_check

Verifies that the Lightwell demo Java application container is running
and that its `/healthz` endpoint returns a healthy response, retrying
both checks before giving up. Sets `health_check_passed` (boolean) so
calling playbooks can decide whether to proceed, fail, or trigger a
rollback.

## Required variables

| Variable | Description |
| --- | --- |
| `app_environment` | `dev` or `prod`; used to compute the default container name and for log messages. |

## Common variables (see `defaults/main.yml`)

| Variable | Default | Description |
| --- | --- | --- |
| `app_port_map` | `{dev: 8082, prod: 8083}` | Host port lookup by `app_environment`. |
| `app_container_name` | `lightwell-java-demo` (`-dev` suffix for `dev`) | Name of the container to check. Defaulted here (rather than only in `deploy_app`) so this role works standalone, e.g. when included from `rollback`. |
| `app_host_port` | `app_port_map[app_environment]` | Host port the application is bound to, derived from `app_environment`. |
| `health_check_container_retries` | `10` | Retries while waiting for the container to report running. |
| `health_check_container_delay` | `3` | Seconds between container status retries. |
| `health_check_http_retries` | `10` | Retries while polling `/healthz`. |
| `health_check_http_delay` | `5` | Seconds between HTTP retries. |
| `health_check_strict` | `true` | When true, fails the play immediately if the health check does not pass. Set to `false` when the caller wants to handle failure itself (e.g. to trigger a rollback). |

## Optional variables

| Variable | Description |
| --- | --- |
| `health_check_host` | Override the host used for the HTTP check (defaults to `ansible_host` or `localhost`). |
