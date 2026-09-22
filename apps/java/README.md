# Lightwell Patch Pipeline Demo App -- Java

A small Spring Boot dashboard used to demo the Lightwell + Ansible patch
pipeline. See the root [README.md](../../README.md) for how this app fits
into the pipeline.

- **SnakeYAML** loads `config/app_config.yaml` -- service metadata,
  tracked dependencies, and a patch timeline -- at startup.
- **Thymeleaf** renders the dashboard, including a live table of
  installed dependency versions. Any version carrying the Lightwell
  `.rhlw-0000X` suffix is called out with a "Lightwell Patched" badge, so a
  Renovate-driven version bump becomes visually obvious.
- **org.json** serializes the `/healthz` and `/api/config` JSON responses.
- **Prism.js** (client-side, loaded from a CDN) syntax-highlights
  `pom.xml` for display on the dashboard.

See the root [README.md](../../README.md) for the full patch pipeline
story (Renovate, EDA, AAP) and [docs/aap-setup.md](../../docs/aap-setup.md)
for AAP resource setup.

## Directory layout

```
apps/java/
├── pom.xml                              # Maven project (Lightwell index primary, Central fallback)
├── Containerfile                        # Multi-stage UBI9 OpenJDK 21 image
├── src/main/java/com/lightwell/demo/
│   ├── LightwellDemoApplication.java    # Spring Boot entry point
│   ├── DashboardController.java         # GET /
│   ├── HealthController.java            # GET /healthz
│   ├── ConfigController.java            # GET /api/config
│   ├── AppConfigService.java            # Loads config/app_config.yaml via SnakeYAML
│   ├── PackageVersionService.java       # Installed version + Lightwell suffix detection
│   ├── PomSnippetService.java           # Reads pom.xml for the dashboard code panel
│   ├── TemplateContextAdvice.java        # Injects githubRepo/appGitSha/appEnv into templates
│   └── model/                           # ServiceInfo, PatchSource, TrackedDependency, ...
├── src/main/resources/
│   ├── application.yml                  # server.port (PORT env override)
│   ├── config/app_config.yaml           # Service metadata, tracked deps, patch timeline
│   ├── templates/
│   │   ├── base.html                    # Layout fragment (nav, footer, Prism.js)
│   │   └── dashboard.html               # Dashboard content
│   └── static/style.css                 # Dark Lightwell-themed CSS
└── src/test/java/com/lightwell/demo/
    └── LightwellDemoApplicationTests.java
```

## Routes

| Method | Path           | Response                                                    |
| ------ | -------------- | ------------------------------------------------------------ |
| `GET`  | `/`            | HTML dashboard                                                |
| `GET`  | `/healthz`     | JSON health status: `{status, service, timestamp, packages}` |
| `GET`  | `/api/config`  | Full YAML config as JSON                                      |

## Tracked dependencies

Lightwell currently remediates `org.json:json`. All five tracked
dependencies -- `spring-core`, `json`, `spring-boot`, `thymeleaf`,
`snakeyaml` -- are
shown on the dashboard; only `json` is pinned to an explicit,
Renovate-visible version in `pom.xml`, so it's the one that will carry
the `.rhlw-0000X` suffix and the "Lightwell" badge once Renovate bumps it
to a remediated version. `spring-core` is pulled in transitively (via
`spring-boot-starter-web`) with no explicit version in `pom.xml`, so
Renovate has nothing to bump for it -- it always shows a "Maven Central"
badge. The rest show a "Maven Central" badge too.

## Local development

```bash
cd apps/java
mvn spring-boot:run
# visit http://localhost:8080
```

The port defaults to `8080` and can be overridden with the `PORT`
environment variable (translated to Spring's `server.port` in
`application.yml`).

`pom.xml` sets the Lightwell Remediated repository as the primary Maven
repository, with Maven Central as fallback. If you need to resolve
Lightwell-remediated artifacts locally (outside of a container build),
authenticate via a Maven `settings.xml` `<server>` entry with your
Lightwell Network service account credentials. Never commit that file --
see the root README's
[credential locations table](../../README.md#where-the-lightwell-service-account-credentials-must-live)
for where these secrets are allowed to live.

## Running tests

```bash
cd apps/java
mvn test
```

`LightwellDemoApplicationTests` covers the dashboard returning 200,
`/healthz` reporting `ok`, and `/api/config` returning service metadata.

## Container build

[`Containerfile`](Containerfile) builds a multi-stage image:

- The **builder** stage (`registry.access.redhat.com/ubi9/openjdk-21`)
  runs `mvn package` authenticated against the Lightwell index via a
  `settings.xml` copied into the build context and removed in the same
  `RUN`. The builder stage is discarded by the multi-stage build, so
  credentials never reach the final image.
- The **runtime** stage
  (`registry.access.redhat.com/ubi9/openjdk-21-runtime`) copies only the
  packaged fat JAR, runs as non-root `USER 1001`, exposes port `8080`,
  and serves via `java -jar app.jar` (embedded Tomcat).
- A `HEALTHCHECK` polls `/healthz` every 30s.

This image is built and deployed by the Ansible collection roles
(`demo.lightwell.build_app` / `demo.lightwell.deploy_app`), not by a
Makefile or compose file in this repo.
