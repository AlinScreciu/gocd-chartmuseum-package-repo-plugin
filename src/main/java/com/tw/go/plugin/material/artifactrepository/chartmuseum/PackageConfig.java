package com.tw.go.plugin.material.artifactrepository.chartmuseum;


import com.tw.go.plugin.material.artifactrepository.chartmuseum.message.PackageMaterialProperties;

public class PackageConfig {
    private final String chartName;
    private String pollFrom = null;
    private String pollTo = null;

    public PackageConfig(PackageMaterialProperties packageConfig) {
        chartName = packageConfig.getProperty(Constants.PACKAGE_NAME).value();
        if (packageConfig.getProperty(Constants.POLL_VERSION_FROM) != null) {
            pollFrom = packageConfig.getProperty(Constants.POLL_VERSION_FROM).value();
        }
        if (packageConfig.getProperty(Constants.POLL_VERSION_TO) != null) {
            pollTo = packageConfig.getProperty(Constants.POLL_VERSION_TO).value();
        }
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