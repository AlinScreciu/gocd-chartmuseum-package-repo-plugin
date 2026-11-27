# Changelog

## 2.0.0 - 2026-11-27

Needs GoCD 23.1.0 or later. See [UPGRADING.md](UPGRADING.md) before you upgrade: **Version to** is now exclusive.

Fixed
- **Version to** is exclusive, as the form has always said (`<`). 1.2 included the bound itself.
- A range with no matching version no longer crashes the poll with `IndexOutOfBoundsException`. It is "no new revision", or an error saying why on a material's first check.
- Packages without `FROM`/`TO` (created with 1.1) no longer fail every poll with a null pointer error.
- Validation results are acted on, instead of computed and thrown away. Invalid configuration gives a clear error.
- **Version from** and **Version to** are optional in the form. Saving a range where from >= to is refused.
- Network errors, HTTP errors and a missing chart are reported with the URL and reason. Before, they showed as `Failed to connect`, `Empty response body` or a bare exception.
- The revision link points at `/api/charts/<name>/<version>`. Before, it pointed at a URL that returned 404.
- `LOCATION` uses the download URL ChartMuseum lists, so it is right when ChartMuseum runs with `--chart-url`.
- Revision timestamps are written in UTC, as the plugin API specifies.
- HTTP calls have timeouts (10 s connect, 60 s request), so a hung ChartMuseum can't block GoCD's material checks.
- A trailing slash in the repository URL is accepted.

Changed
- `target-go-version` is 23.1.0, the oldest GoCD release it is tested on. It was 14.0.0.
- Built for Java 11. Compiled against go-plugin-api 23.1.0.
- Only gson and maven-artifact are bundled. The jar is 0.4 MB instead of 18 MB: JUnit, Testcontainers, httpclient and slf4j-simple were bundled by mistake.
- `plugin.xml` takes its version from the pom. It said 1.0-SNAPSHOT in every release, and the description said "Polls a Maven repository".

Added
- A compatibility test that boots real GoCD servers (23.1.0 to 26.2.0) in Docker with the plugin installed and checks that pipelines trigger. CI runs it on every push.
- README sections on install, configuration, version ordering, job variables and errors. Also this changelog and UPGRADING.md.
- The release workflow runs the full test matrix and publishes the release notes from this file.

## 1.2 - 2024-02-14

- Optional version range per package (**Version to poll >=** / **Version to poll <**).

## 1.1 - 2023-09-28

- Poll on the chart `version` rather than `appVersion`.
- Fixed repository and package validation.

## 1.0-SNAPSHOT - 2023-09-23

- First build: poll ChartMuseum's `/api/charts/<name>` and report the newest chart, keyed on `appVersion`. The job gets `VERSION` and `LOCATION`.
