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
import org.apache.maven.artifact.versioning.ComparableVersion;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.List;

public class PackageRepositoryPoller {

    private final PackageRepositoryConfigurationProvider configurationProvider;
    private static final Logger LOGGER = Logger.getLoggerFor(PackageRepositoryPoller.class);

    public PackageRepositoryPoller(PackageRepositoryConfigurationProvider configurationProvider) {
        this.configurationProvider = configurationProvider;
    }

    public CheckConnectionResultMessage checkConnectionToRepository(PackageMaterialProperties repositoryConfiguration) throws IOException {
        configurationProvider.validateRepositoryConfiguration(repositoryConfiguration);
        String url = repositoryConfiguration.getProperty(Constants.REPO_URL).value();
        LOGGER.info("Checking connection to server: '" + url + "'");
        ChartmuseumClient chartmuseumClient = new ChartmuseumClient(url);
        try {
            chartmuseumClient.checkRepoConnection();
            LOGGER.info("Successfully connected to server: '" + url + "'");
            return new CheckConnectionResultMessage(CheckConnectionResultMessage.STATUS.SUCCESS, List.of("Successfully connected"));
        } catch (Exception e) {
            LOGGER.info(String.format("Failed connection to server: '%s'", url));
            return new CheckConnectionResultMessage(CheckConnectionResultMessage.STATUS.FAILURE, List.of("Failed to connect"));
        }
    }

    public CheckConnectionResultMessage checkConnectionToPackage(PackageMaterialProperties packageConfiguration, PackageMaterialProperties repositoryConfiguration) {
        configurationProvider.validatePackageConfiguration(packageConfiguration);
        configurationProvider.validateRepositoryConfiguration(repositoryConfiguration);
        String url = repositoryConfiguration.getProperty(Constants.REPO_URL).value();
        String chartName = packageConfiguration.getProperty(Constants.PACKAGE_NAME).value();
        LOGGER.info(String.format("Checking existence of chart: '%s' in chartmuseum: '%s'", chartName, url));
        ChartmuseumClient chartmuseumClient = new ChartmuseumClient(url);
        try {
            chartmuseumClient.checkChartConnection(chartName);
            LOGGER.info(String.format("Found chart: '%s' in chartmuseum: '%s'", chartName, url));
            return new CheckConnectionResultMessage(CheckConnectionResultMessage.STATUS.SUCCESS, List.of("success message"));

        } catch (Exception e) {
            LOGGER.info(String.format("Failed to find chart: '%s' in chartmuseum: '%s'", chartName, url));
            return new CheckConnectionResultMessage(CheckConnectionResultMessage.STATUS.FAILURE, List.of("Failed to connect"));
        }
    }

    public PackageRevisionMessage getLatestRevision(PackageMaterialProperties packageConfiguration, PackageMaterialProperties repositoryConfiguration) {
        configurationProvider.validatePackageConfiguration(packageConfiguration);
        configurationProvider.validateRepositoryConfiguration(repositoryConfiguration);
        String url = repositoryConfiguration.getProperty(Constants.REPO_URL).value();
        String chart = packageConfiguration.getProperty(Constants.PACKAGE_NAME).value();
        ChartmuseumClient chartmuseumClient = new ChartmuseumClient(url);
        LOGGER.info(String.format("Getting latest revision of chart: '%s' in chartmuseum: '%s'", chart, url));
        Chart latestRevision = chartmuseumClient.getLatestRevision(chart);
        if (latestRevision == null) {
            LOGGER.info(String.format("Didn't find chart: '%s' in chartmuseum: '%s'", chart, url));
            return null;
        }
        LOGGER.info(String.format("Found revision: '%s' of chart: '%s' in chartmuseum: '%s'", latestRevision.getVersion(), chart, url));

        return getPackageRevisionMessage(url, chart, latestRevision);
    }

    public PackageRevisionMessage getLatestRevisionSince(PackageMaterialProperties packageConfiguration, PackageMaterialProperties repositoryConfiguration, PackageRevisionMessage previousPackageRevision) {
        configurationProvider.validatePackageConfiguration(packageConfiguration);
        configurationProvider.validateRepositoryConfiguration(repositoryConfiguration);
        String url = repositoryConfiguration.getProperty(Constants.REPO_URL).value();
        String chart = packageConfiguration.getProperty(Constants.PACKAGE_NAME).value();
        ChartmuseumClient chartmuseumClient = new ChartmuseumClient(url);
        LOGGER.info(String.format("Getting latest revision of chart: '%s' since: '%s' in chartmuseum: '%s'", chart, previousPackageRevision.getRevision(), url));
        Chart latestRevision = chartmuseumClient.getLatestRevision(chart);
        String previousRevision = previousPackageRevision.getRevision();
        int compare = new ComparableVersion(latestRevision.getVersion()).compareTo(new ComparableVersion(previousRevision));
        if (compare > 0) {
            LOGGER.info(String.format("Found new revision: '%s' of chart: '%s' since: '%s' in chartmuseum: '%s'", latestRevision.getVersion(), chart, previousPackageRevision.getRevision(), url));
            return getPackageRevisionMessage(url, chart, latestRevision);
        }

        LOGGER.info(String.format("Didnt find new revision of chart: '%s' since: '%s' in chartmuseum: '%s'", chart, previousPackageRevision.getRevision(), url));
        return null;

    }

    @NotNull
    private PackageRevisionMessage getPackageRevisionMessage(String url, String chart, Chart latestRevision) {
        PackageRevisionMessage packageRevisionMessage = new PackageRevisionMessage(latestRevision.getVersion(), latestRevision.getCreated(), "chartmuseum", latestRevision.getDescription(), url + "/api/" + latestRevision.getUrls().get(0));
        packageRevisionMessage.addData("VERSION", latestRevision.getVersion());
        packageRevisionMessage.addData("LOCATION", url + "/charts/" + chart + "-" + latestRevision.getVersion() + ".tgz");
        return packageRevisionMessage;
    }
}
