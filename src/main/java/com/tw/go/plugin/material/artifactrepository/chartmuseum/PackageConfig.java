package com.tw.go.plugin.material.artifactrepository.chartmuseum;


import com.tw.go.plugin.material.artifactrepository.chartmuseum.message.PackageMaterialProperties;

public class PackageConfig {
    private final String chartName;
    private String pollFrom = null;
    private String pollTo = null;

    public PackageConfig(PackageMaterialProperties packageConfig) {
        chartName = packageConfig.getProperty(Constants.PACKAGE_NAME).value();
        if (packageConfig.getProperty(Constants.POLL_VERSION_FROM) != null && !packageConfig.getProperty(Constants.POLL_VERSION_FROM).equals("")) {
            pollFrom = packageConfig.getProperty(Constants.POLL_VERSION_FROM).value();
        }
        if (packageConfig.getProperty(Constants.POLL_VERSION_TO) != null && !packageConfig.getProperty(Constants.POLL_VERSION_TO).equals("")) {
            pollTo = packageConfig.getProperty(Constants.POLL_VERSION_TO).value();
        }
    }

    public PackageConfig(String chartName, String pollFrom, String pollTo) {
        this.chartName = chartName;
        if (pollFrom != null && !pollFrom.equals("")) {
            this.pollFrom = pollFrom;
        }
        if (pollTo != null && !pollTo.equals("")) {
            this.pollTo = pollTo;
        }
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