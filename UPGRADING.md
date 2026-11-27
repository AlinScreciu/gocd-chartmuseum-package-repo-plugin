# Upgrading

## From 1.1 or 1.2 to 2.0.0

Short version: check that GoCD is 23.1.0 or later, swap the jar, restart. Then check any package whose **Version to** is set; see step 4.

### 1. Check the GoCD version

2.0.0 needs GoCD 23.1.0 or later. On an older server GoCD marks the plugin invalid ("Incompatible with GoCD version") and the materials stop polling. Upgrade GoCD first, or stay on 1.2.

### 2. Replace the jar

1. Delete the old `chartmuseum.material.poller.jar` from `<GoCD server>/plugins/external/`. Keep only one copy: if two jars have the plugin id `chartmuseum-repo`, GoCD rejects both.
2. Put the 2.0.0 jar there.
3. Restart the GoCD server.
4. Check that the plugin is active under **Admin > Plugins** and shows version 2.0.0.

On the Docker image, change the URL in `GOCD_PLUGIN_INSTALL_chartmuseum` to the 2.0.0 release and recreate the container. The image overwrites the old jar.

### 3. Existing configuration keeps working

The plugin id, the property keys (`REPO_URL`, `PACKAGE_NAME`, `FROM`, `TO`) and which properties count toward the material's identity have not changed. Material fingerprints stay the same: pipelines do not re-trigger because of the upgrade, and history is kept.

Packages created with 1.1 have no `FROM`/`TO` at all. In 1.2 they failed on every poll with a null pointer error. 2.0.0 treats them as "no range" and polls them normally.

### 4. Check packages that set "Version to"

**This is the one behaviour change that can need action.**

The form has always said `Version to poll <`, but 1.2 actually kept versions up to and *including* that value. 2.0.0 does what the label says: the upper bound is exclusive.

You are only affected if a chart version exactly equal to your **Version to** value exists. For example, with `TO = 1.5.0`:

| Chart version | 1.2 | 2.0.0 |
|---|---|---|
| `1.4.9` | picked | picked |
| `1.5.0-rc1` | picked | picked |
| `1.5.0` | picked | **not picked** |
| `1.5.0-1` (numeric suffix) | not picked | not picked |

If you meant "up to and including 1.5.0", set **Version to** to the next version, for example `1.5.1` or `1.6.0`.

Nothing else happens automatically. If a pipeline last ran on `1.5.0` and that version is now outside the range, GoCD keeps that revision as its last one and triggers again only when a newer version comes into the range.

### 5. Smaller differences you may notice

- **Version from** and **Version to** are now optional in the form. 1.2 marked them required, although an empty value always meant "no bound".
- **Saving a package with Version from >= Version to** is refused. Such a range can never match.
- **Error messages say what went wrong.** In 1.2 most failures showed as `Failed to connect`, `Empty response body` or a bare `NullPointerException` / `IndexOutOfBoundsException`. They now name the URL, the HTTP status, a missing chart, or a range with no versions.
- **A range with no matching version** no longer crashes the poll. On a material's first check it is reported as an error with the reason. After that it counts as "no new revision".
- **The revision link** in the GoCD UI now opens `/api/charts/<name>/<version>`. 1.2 linked to a URL that ChartMuseum answers with 404.
- **`LOCATION`** now comes from the download URL ChartMuseum lists for the chart. With ChartMuseum's default settings it is the same URL as before. With `--chart-url`, it is now that absolute URL.
- **Revision timestamps** are sent in UTC, as the plugin API specifies. GoCD 26.1 and later read them as UTC. On GoCD 25.4 and older, the "modified" time of a chart revision moves by the server's offset from UTC, unless the server already runs in UTC. The Docker images run in UTC by default. Triggering does not use this time.
- **The jar is 0.4 MB instead of 18 MB.** 1.2 bundled JUnit, Testcontainers and a logging backend that it did not need at run time.

### Rolling back

Put the 1.2 jar back in place of the 2.0.0 jar and restart. The configuration format is the same, so nothing else needs to change. If you changed a **Version to** value in step 4, set it back.
