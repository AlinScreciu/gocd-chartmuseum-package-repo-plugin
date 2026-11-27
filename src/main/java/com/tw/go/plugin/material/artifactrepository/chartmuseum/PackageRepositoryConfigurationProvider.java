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


import com.tw.go.plugin.material.artifactrepository.chartmuseum.message.PackageMaterialProperties;
import com.tw.go.plugin.material.artifactrepository.chartmuseum.message.PackageMaterialProperty;
import com.tw.go.plugin.material.artifactrepository.chartmuseum.message.ValidationError;
import com.tw.go.plugin.material.artifactrepository.chartmuseum.message.ValidationResultMessage;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.regex.Pattern;


public class PackageRepositoryConfigurationProvider {

    // Helm chart names are letters, digits, '-', '_' and '.'. Anything else (a '/', say)
    // would change which ChartMuseum endpoint the plugin calls.
    private static final Pattern CHART_NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]*");

    public PackageMaterialProperties repositoryConfiguration() {
        PackageMaterialProperties repositoryConfigurationResponse = new PackageMaterialProperties();
        repositoryConfigurationResponse.addPackageMaterialProperty(Constants.REPO_URL, url());
        return repositoryConfigurationResponse;
    }

    public PackageMaterialProperties packageConfiguration() {
        PackageMaterialProperties packageConfigurationResponse = new PackageMaterialProperties();
        packageConfigurationResponse.addPackageMaterialProperty(Constants.PACKAGE_NAME, packageSpec());
        packageConfigurationResponse.addPackageMaterialProperty(Constants.FROM, from());
        packageConfigurationResponse.addPackageMaterialProperty(Constants.TO, to());

        return packageConfigurationResponse;
    }

    /** The trimmed value of a property, or "" when the property is missing (e.g. a package saved by plugin 1.1). */
    public static String value(PackageMaterialProperties configuration, String key) {
        if (configuration == null) {
            return "";
        }
        PackageMaterialProperty property = configuration.getProperty(key);
        if (property == null || property.value() == null) {
            return "";
        }
        return property.value().trim();
    }

    public ValidationResultMessage validateRepositoryConfiguration(PackageMaterialProperties configurationProvidedByUser) {
        ValidationResultMessage validationResultMessage = new ValidationResultMessage();
        String url = value(configurationProvidedByUser, Constants.REPO_URL);
        if (url.isEmpty()) {
            validationResultMessage.addError(ValidationError.create(Constants.REPO_URL, "Repository URL is required"));
            return validationResultMessage;
        }
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme();
            if (!("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) || uri.getHost() == null) {
                validationResultMessage.addError(ValidationError.create(Constants.REPO_URL, "Repository URL must be an http:// or https:// URL, e.g. http://chartmuseum:8080"));
            }
        } catch (URISyntaxException e) {
            validationResultMessage.addError(ValidationError.create(Constants.REPO_URL, "Repository URL is not a valid URL: " + e.getMessage()));
        }
        return validationResultMessage;
    }

    public ValidationResultMessage validatePackageConfiguration(PackageMaterialProperties configurationProvidedByUser) {
        ValidationResultMessage validationResultMessage = new ValidationResultMessage();
        String chartName = value(configurationProvidedByUser, Constants.PACKAGE_NAME);
        if (chartName.isEmpty()) {
            validationResultMessage.addError(ValidationError.create(Constants.PACKAGE_NAME, "Chart name is required"));
        } else if (!CHART_NAME.matcher(chartName).matches()) {
            validationResultMessage.addError(ValidationError.create(Constants.PACKAGE_NAME, "Chart name may only contain letters, digits, '.', '_' and '-'"));
        }
        VersionRange range = versionRange(configurationProvidedByUser);
        if (!range.isSatisfiable()) {
            validationResultMessage.addError(ValidationError.create(Constants.TO, "Version to must be greater than Version from"));
        }
        return validationResultMessage;
    }

    public VersionRange versionRange(PackageMaterialProperties packageConfiguration) {
        return VersionRange.parse(value(packageConfiguration, Constants.FROM), value(packageConfiguration, Constants.TO));
    }

    private PackageMaterialProperty url() {
        return new PackageMaterialProperty().withDisplayName("Repository URL").withRequired(true).withPartOfIdentity(true).withDisplayOrder("0");
    }

    private PackageMaterialProperty packageSpec() {
        return new PackageMaterialProperty().withDisplayName("Chart name").withRequired(true).withPartOfIdentity(true).withDisplayOrder("0");
    }

    // FROM and TO keep partOfIdentity=true: changing them would change the fingerprint of
    // every package material configured with plugin 1.2.
    private PackageMaterialProperty from() {
        return new PackageMaterialProperty().withDisplayName("Version from (>=, optional)").withRequired(false).withPartOfIdentity(true).withDisplayOrder("1");
    }

    private PackageMaterialProperty to() {
        return new PackageMaterialProperty().withDisplayName("Version to (<, optional)").withRequired(false).withPartOfIdentity(true).withDisplayOrder("2");
    }
}
