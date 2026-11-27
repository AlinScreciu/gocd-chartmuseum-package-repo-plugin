# gocd-chartmuseum-package-repo-plugin

A GoCD package repository plugin for [ChartMuseum](https://github.com/helm/chartmuseum). A pipeline with a package material triggers when a newer version of a Helm chart lands in ChartMuseum. You can limit it to a version range, so a pipeline follows one release line.

GoCD ships pollers for Maven, Yum and npm, but none for Helm charts. This plugin fills that gap.

- [Compatibility](#compatibility)
- [Install](#install)
- [Configure](#configure)
- [Using the chart in a job](#using-the-chart-in-a-job)
- [Errors](#errors)
- [Build and test](#build-and-test)
- [Release](#release)
- Upgrading from 1.x: [UPGRADING.md](UPGRADING.md). Changes per version: [CHANGELOG.md](CHANGELOG.md).

## Compatibility

| | |
|---|---|
| GoCD server | 23.1.0 or later. Every release is tested against 23.1.0, 23.3.0, 23.5.0, 24.5.0, 25.4.0, 26.1.0 and 26.2.0. On a server older than 23.1.0, GoCD marks the plugin invalid ("Incompatible with GoCD version"); use plugin 1.2 there. |
| Java | The plugin runs inside the GoCD server JVM and needs Java 11 or later. Every GoCD release from 23.1.0 on meets that. |
| ChartMuseum | Tested with v0.16.3. The plugin calls `GET /health` and `GET /api/charts/<name>` only, which ChartMuseum has had since its early releases. |
| GoCD plugin API | `package-repository` extension, version 1.0. Plugin id `chartmuseum-repo`. |

Authentication is not supported: ChartMuseum must allow anonymous reads of `/api/charts`.

## Install

1. Download `chartmuseum.material.poller.jar` from the [latest release](https://github.com/AlinScreciu/gocd-chartmuseum-package-repo-plugin/releases/latest).
2. Put it in `<GoCD server>/plugins/external/`. Leave no other copy of the plugin in that folder: if two jars have the same plugin id, GoCD rejects both.
3. Restart the GoCD server.
4. Check that the plugin is active under **Admin > Plugins**.

On the official `gocd/gocd-server` Docker image, set an environment variable instead. The image downloads the jar into `plugins/external` when it starts:

```
GOCD_PLUGIN_INSTALL_chartmuseum=https://github.com/AlinScreciu/gocd-chartmuseum-package-repo-plugin/releases/download/gocd-chartmuseum-package-repo-plugin-2.0.0/chartmuseum.material.poller.jar
```

From image 26.2.0 on, you can append `@<sha256>` to that URL, using the SHA-256 from the release notes. The image then refuses a jar that does not match.

## Configure

**Admin > Pipelines > Materials > Package**, or **Admin > Package Repositories**.

Repository (one per ChartMuseum server):

| Field | Key | Required | Meaning |
|---|---|---|---|
| Repository URL | `REPO_URL` | yes | Base URL of ChartMuseum, for example `http://chartmuseum:8080`. A trailing slash is fine. |

Package (one per chart, or per chart and version range):

| Field | Key | Required | Meaning |
|---|---|---|---|
| Chart name | `PACKAGE_NAME` | yes | The chart's name, for example `guestbook`. |
| Version from (>=, optional) | `FROM` | no | Lowest version to consider. Inclusive. |
| Version to (<, optional) | `TO` | no | Upper bound. Exclusive: the version itself is not considered. |

With no range set, the material follows the newest version of the chart. A new pipeline run starts whenever a version newer than the last one GoCD saw shows up inside the range.

### How versions are ordered

Versions are compared with Maven's `ComparableVersion`, not as strings:

- `1.10.0` is newer than `1.9.0`.
- `2.0.0-rc1`, `2.0.0-beta` and `2.0.0-SNAPSHOT` are older than `2.0.0`.
- A numeric suffix sorts after the bare version: `1.4.0-123` is newer than `1.4.0`.

So to follow the 1.4 line, set **Version from** `1.4.0` and **Version to** `1.5.0`. That covers `1.4.0`, `1.4.7`, `1.4.7-20240101` and `1.5.0-rc1`, but not `1.5.0`. To leave out the 1.5 pre-releases, use **Version to** `1.5.0-alpha`, or whatever your earliest pre-release tag is.

### Checking the setup

**Check connection** on the repository calls ChartMuseum's `/health`. **Check package** fetches the chart and reports the newest version in the range. It fails if the chart does not exist or if no version matches the range.

## Using the chart in a job

GoCD passes the package revision to every job of the pipeline as environment variables. Their names are built from the repository and package names, upper-cased, with other characters turned into `_`. For repository `chartmuseum` and package `guestbook`:

| Variable | Example |
|---|---|
| `GO_PACKAGE_CHARTMUSEUM_GUESTBOOK_VERSION` | `1.4.7` |
| `GO_PACKAGE_CHARTMUSEUM_GUESTBOOK_LOCATION` | `http://chartmuseum:8080/charts/guestbook-1.4.7.tgz` |
| `GO_PACKAGE_CHARTMUSEUM_GUESTBOOK_LABEL` | `1.4.7` (set by GoCD) |
| `GO_REPO_CHARTMUSEUM_GUESTBOOK_REPO_URL` | `http://chartmuseum:8080` (set by GoCD) |

`LOCATION` is the download URL that ChartMuseum lists for the chart. If ChartMuseum runs with `--chart-url`, that is the absolute URL it advertises.

```sh
helm upgrade --install guestbook "$GO_PACKAGE_CHARTMUSEUM_GUESTBOOK_LOCATION"
```

The revision's link in the GoCD UI opens `/api/charts/<name>/<version>` on ChartMuseum.

## Errors

| Situation | What GoCD shows |
|---|---|
| ChartMuseum unreachable, or a non-2xx answer | The material check fails, with the URL and the HTTP status or connection error. |
| Chart does not exist | The material check fails: `Chart '<name>' not found in ChartMuseum at <url>`. |
| No version in the range, on a material's first check | The material check fails: `Chart '<name>' has no version matching <range> ...`. |
| No version in the range, after earlier runs | Nothing. It counts as "no new revision". |
| Version from >= Version to | GoCD rejects the package configuration when you save it. |

Plugin log lines go to `plugin-chartmuseum-repo.log` in the GoCD server's log folder.

## Build and test

Needs JDK 17 or later, Maven and Docker. The tests start ChartMuseum in Docker with Testcontainers.

```sh
mvn package                     # unit tests and ChartMuseum tests; jar in target/
mvn verify -Pgocd-compat -Dgocd.version=23.1.0 -Dtest=none -Dsurefire.failIfNoSpecifiedTests=false
```

The `gocd-compat` profile starts a real `gocd/gocd-server` container of the given version with the built jar installed. It configures a repository, two packages and two pipelines through the GoCD API, then checks that:

- the plugin loads,
- both pipelines trigger on the right chart versions,
- uploading a newer chart triggers the unbounded pipeline but not the one with a range.

CI runs it for every GoCD version in the compatibility table. GoCD images before 24.1.0 are amd64 only, so on Apple silicon they run under emulation and take several minutes each.

The test fixtures in `src/test/resources/charts` are about 4,750 generated `guestbook` chart archives, with versions picked to exercise the ordering rules above. They are test data, not code.

On Rancher Desktop, Testcontainers needs:

```sh
export DOCKER_HOST=unix://$HOME/.rd/docker.sock TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
```

## Release

1. Set `<version>` in `pom.xml`. `plugin.xml` takes its version from there.
2. Add a `## <version> - <date>` section at the top of `CHANGELOG.md`, and an `UPGRADING.md` section if users have to do anything.
3. Merge to `main`, then tag and push the tag:
   ```sh
   git tag gocd-chartmuseum-package-repo-plugin-<version>
   git push origin gocd-chartmuseum-package-repo-plugin-<version>
   ```
4. The `Release` workflow runs the test matrix, checks that the tag matches the pom, and publishes a GitHub release. The release has the jar, the changelog section and the checksums.

## License

Apache 2.0. The plugin skeleton (`PackageRepositoryMaterial`, the `message` classes) is adapted from ThoughtWorks' sample GoCD package material plugin, and keeps its copyright header.
