/*************************GO-LICENSE-START*********************************
 * Copyright 2014 ThoughtWorks, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *************************GO-LICENSE-END***********************************/
package com.tw.go.plugin.material.artifactrepository.chartmuseum;

import com.thoughtworks.go.plugin.api.logging.Logger;
import com.tw.go.plugin.material.artifactrepository.chartmuseum.message.CheckConnectionResultMessage;
import com.tw.go.plugin.material.artifactrepository.chartmuseum.message.PackageMaterialProperties;
import com.tw.go.plugin.material.artifactrepository.chartmuseum.message.PackageRevisionMessage;
import com.tw.go.plugin.material.artifactrepository.chartmuseum.message.ValidationResultMessage;
import org.apache.maven.artifact.versioning.ComparableVersion;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.tw.go.plugin.material.artifactrepository.chartmuseum.message.CheckConnectionResultMessage.STATUS.FAILURE;
import static com.tw.go.plugin.material.artifactrepository.chartmuseum.message.CheckConnectionResultMessage.STATUS.SUCCESS;

/**
 * Error handling follows what GoCD does with each answer:
 * - check-* calls return FAILURE with a readable message, which GoCD shows next to the form.
 * - latest-revision throws when nothing matches: GoCD treats an empty answer as an error anyway,
 *   and the message tells the user why.
 * - latest-revision-since returns null when there is nothing newer, which GoCD reads as "no change".
 * - Network and HTTP errors are thrown, so GoCD marks the material check as failed instead of quietly
 *   reporting "no change" while ChartMuseum is down.
 */
public class PackageRepositoryPoller {

    private final PackageRepositoryConfigurationProvider configurationProvider;
    private static final Logger LOGGER = Logger.getLoggerFor(PackageRepositoryPoller.class);

    public PackageRepositoryPoller(PackageRepositoryConfigurationProvider configurationProvider) {
        this.configurationProvider = configurationProvider;
    }

    public CheckConnectionResultMessage checkConnectionToRepository(PackageMaterialProperties repositoryConfiguration) {
        ValidationResultMessage validation = configurationProvider.validateRepositoryConfiguration(repositoryConfiguration);
        if (validation.failure()) {
            return new CheckConnectionResultMessage(FAILURE, validation.getMessages());
        }
        String url = PackageRepositoryConfigurationProvider.value(repositoryConfiguration, Constants.REPO_URL);
        LOGGER.info(String.format("Checking connection to ChartMuseum '%s'", url));
        try {
            new ChartmuseumClient(url).checkRepoConnection();
            return new CheckConnectionResultMessage(SUCCESS, List.of("Successfully connected to " + url));
        } catch (IOException e) {
            LOGGER.warn(String.format("Failed to connect to ChartMuseum '%s': %s", url, e.getMessage()));
            return new CheckConnectionResultMessage(FAILURE, List.of("Failed to connect: " + e.getMessage()));
        }
    }

    public CheckConnectionResultMessage checkConnectionToPackage(PackageMaterialProperties packageConfiguration, PackageMaterialProperties repositoryConfiguration) {
        List<String> errors = validationErrors(packageConfiguration, repositoryConfiguration);
        if (!errors.isEmpty()) {
            return new CheckConnectionResultMessage(FAILURE, errors);
        }
        Settings settings = settings(packageConfiguration, repositoryConfiguration);
        LOGGER.info(String.format("Checking chart '%s' (%s) in ChartMuseum '%s'", settings.chart, settings.range, settings.url));
        try {
            Optional<Chart> latest = settings.client().getLatestRevision(settings.chart, settings.range);
            if (latest.isEmpty()) {
                return new CheckConnectionResultMessage(FAILURE, List.of(noMatchMessage(settings)));
            }
            return new CheckConnectionResultMessage(SUCCESS, List.of("Found chart " + settings.chart + ", latest version in range: " + latest.get().getVersion()));
        } catch (IOException e) {
            LOGGER.warn(String.format("Failed to check chart '%s' in ChartMuseum '%s': %s", settings.chart, settings.url, e.getMessage()));
            return new CheckConnectionResultMessage(FAILURE, List.of(e.getMessage()));
        }
    }

    public PackageRevisionMessage getLatestRevision(PackageMaterialProperties packageConfiguration, PackageMaterialProperties repositoryConfiguration) throws IOException {
        Settings settings = validSettings(packageConfiguration, repositoryConfiguration);
        LOGGER.info(String.format("Getting latest revision of chart '%s' (%s) in ChartMuseum '%s'", settings.chart, settings.range, settings.url));
        ChartmuseumClient client = settings.client();
        Optional<Chart> latest = client.getLatestRevision(settings.chart, settings.range);
        if (latest.isEmpty()) {
            throw new IllegalStateException(noMatchMessage(settings));
        }
        LOGGER.info(String.format("Found revision '%s' of chart '%s'", latest.get().getVersion(), settings.chart));
        return toRevisionMessage(client, latest.get());
    }

    public PackageRevisionMessage getLatestRevisionSince(PackageMaterialProperties packageConfiguration, PackageMaterialProperties repositoryConfiguration, PackageRevisionMessage previousPackageRevision) throws IOException {
        Settings settings = validSettings(packageConfiguration, repositoryConfiguration);
        String previous = previousPackageRevision == null ? null : previousPackageRevision.getRevision();
        LOGGER.info(String.format("Getting latest revision of chart '%s' (%s) since '%s' in ChartMuseum '%s'", settings.chart, settings.range, previous, settings.url));
        ChartmuseumClient client = settings.client();
        Optional<Chart> latest = client.getLatestRevision(settings.chart, settings.range);
        if (latest.isEmpty()) {
            LOGGER.info(String.format("No version of chart '%s' matches %s", settings.chart, settings.range));
            return null;
        }
        if (previous == null || new ComparableVersion(latest.get().getVersion()).compareTo(new ComparableVersion(previous)) > 0) {
            LOGGER.info(String.format("Found new revision '%s' of chart '%s' since '%s'", latest.get().getVersion(), settings.chart, previous));
            return toRevisionMessage(client, latest.get());
        }
        LOGGER.info(String.format("Didn't find a new revision of chart '%s' since '%s'", settings.chart, previous));
        return null;
    }

    private PackageRevisionMessage toRevisionMessage(ChartmuseumClient client, Chart chart) {
        PackageRevisionMessage message = new PackageRevisionMessage(chart.getVersion(), chart.getCreated(), "chartmuseum", chart.getDescription(), client.chartVersionUrl(chart));
        message.addData("VERSION", chart.getVersion());
        message.addData("LOCATION", client.downloadUrl(chart));
        return message;
    }

    private static String noMatchMessage(Settings settings) {
        return String.format("Chart '%s' has no version matching %s in ChartMuseum %s", settings.chart, settings.range, settings.url);
    }

    private List<String> validationErrors(PackageMaterialProperties packageConfiguration, PackageMaterialProperties repositoryConfiguration) {
        List<String> errors = new ArrayList<>();
        errors.addAll(configurationProvider.validateRepositoryConfiguration(repositoryConfiguration).getMessages());
        errors.addAll(configurationProvider.validatePackageConfiguration(packageConfiguration).getMessages());
        return errors;
    }

    private Settings validSettings(PackageMaterialProperties packageConfiguration, PackageMaterialProperties repositoryConfiguration) {
        List<String> errors = validationErrors(packageConfiguration, repositoryConfiguration);
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException("Invalid configuration: " + String.join("; ", errors));
        }
        return settings(packageConfiguration, repositoryConfiguration);
    }

    private Settings settings(PackageMaterialProperties packageConfiguration, PackageMaterialProperties repositoryConfiguration) {
        return new Settings(
                PackageRepositoryConfigurationProvider.value(repositoryConfiguration, Constants.REPO_URL),
                PackageRepositoryConfigurationProvider.value(packageConfiguration, Constants.PACKAGE_NAME),
                configurationProvider.versionRange(packageConfiguration));
    }

    private static final class Settings {
        final String url;
        final String chart;
        final VersionRange range;

        Settings(String url, String chart, VersionRange range) {
            this.url = url;
            this.chart = chart;
            this.range = range;
        }

        ChartmuseumClient client() {
            return new ChartmuseumClient(url);
        }
    }
}
