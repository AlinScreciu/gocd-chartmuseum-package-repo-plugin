/*************************GO-LICENSE-START*********************************
 * Copyright 2014 ThoughtWorks, Inc.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *    http://www.apache.org/licenses/LICENSE-2.0
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


public class PackageRepositoryConfigurationProvider {
    
    public PackageMaterialProperties repositoryConfiguration() {
        PackageMaterialProperties repositoryConfigurationResponse = new PackageMaterialProperties();
        repositoryConfigurationResponse.addPackageMaterialProperty(Constants.REPO_URL, url());
        return repositoryConfigurationResponse;
    }
    
    public PackageMaterialProperties packageConfiguration() {
        PackageMaterialProperties packageConfigurationResponse = new PackageMaterialProperties();
        packageConfigurationResponse.addPackageMaterialProperty(Constants.PACKAGE_NAME, packageSpec());
        packageConfigurationResponse.addPackageMaterialProperty(Constants.POLL_VERSION_FROM, pollVersionFrom());
        packageConfigurationResponse.addPackageMaterialProperty(Constants.POLL_VERSION_TO, pollVersionTo());
        return packageConfigurationResponse;
    }
    
    public ValidationResultMessage validateRepositoryConfiguration(PackageMaterialProperties configurationProvidedByUser) {
        ValidationResultMessage validationResultMessage = new ValidationResultMessage();
        if (configurationProvidedByUser.getProperty(Constants.REPO_URL).value().isEmpty()) {
            validationResultMessage.addError(ValidationError.create(Constants.REPO_URL, "Repository URL is null"));
        }
        return validationResultMessage;
    }
    
    public ValidationResultMessage validatePackageConfiguration(PackageMaterialProperties configurationProvidedByUser) {
        ValidationResultMessage validationResultMessage = new ValidationResultMessage();
        if (configurationProvidedByUser.getProperty(Constants.PACKAGE_NAME).value().isEmpty()) {
            validationResultMessage.addError(ValidationError.create(Constants.PACKAGE_NAME, "Package name is null"));
        }
        
        return validationResultMessage;
    }
    
    
    private PackageMaterialProperty url() {
        return new PackageMaterialProperty().withDisplayName("Repository URL").withRequired(true).withPartOfIdentity(true).withDisplayOrder("0");
    }
    
    private PackageMaterialProperty packageSpec() {
        return new PackageMaterialProperty().withDisplayName("Package Name").withRequired(true).withPartOfIdentity(true).withDisplayOrder("0");
    }
    
    private PackageMaterialProperty pollVersionFrom() {
        return new PackageMaterialProperty().withDisplayName("Poll Version From").withRequired(false).withPartOfIdentity(false).withDisplayOrder("1");
    }
    
    private PackageMaterialProperty pollVersionTo() {
        return new PackageMaterialProperty().withDisplayName("Poll Version To").withRequired(false).withPartOfIdentity(false).withDisplayOrder("2");
    }
}
