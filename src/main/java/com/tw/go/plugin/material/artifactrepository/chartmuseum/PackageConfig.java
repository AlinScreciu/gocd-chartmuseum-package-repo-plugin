package com.tw.go.plugin.material.artifactrepository.chartmuseum;


import com.tw.go.plugin.material.artifactrepository.chartmuseum.message.PackageMaterialProperties;

public class PackageConfig {
    private final String chartName;
    private final String pollFrom;
    private final String pollTo;
    
    public PackageConfig(PackageMaterialProperties packageConfig) {
        chartName = packageConfig.getProperty(Constants.PACKAGE_NAME).value();
        pollFrom = packageConfig.getProperty(Constants.POLL_VERSION_FROM).value();
        pollTo = packageConfig.getProperty(Constants.POLL_VERSION_TO).value();
    }
    
    public PackageConfig(String chartName, String pollFrom, String pollTo) {
        this.chartName = chartName;
        this.pollFrom = pollFrom;
        this.pollTo = pollTo;
    }
    
    public String getChartName() {
        return chartName;
    }
    
    public String getPollFrom() {
        return pollFrom;
    }
    
    public String getPollTo() {
        return pollTo;
    }
}