package com.tw.go.plugin.material.artifactrepository.chartmuseum;

import java.util.Date;
import java.util.List;
import java.util.Objects;

public class Chart {
    private String name;
    private String version;
    private String description;
    private String apiVersion;
    private String appVersion;
    private List<String> urls;
    private Date created;
    private String digest;
    
    public Chart() {
    
    }
    
    public Chart(String name, String version, String description, String apiVersion, String appVersion, List<String> urls, Date created, String digest) {
        this.name = name;
        this.version = version;
        this.description = description;
        this.apiVersion = apiVersion;
        this.appVersion = appVersion;
        this.urls = urls;
        this.created = created;
        this.digest = digest;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }

//    public String getSemver() {
//        String left = version.split("-")[0];
//        StringBuilder stringBuilder = new StringBuilder();
//        int dots = 0;
//        for (int i = 0; i < left.length(); i++) {
//            char currentChar = left.charAt(i);
//            stringBuilder.append(currentChar);
//            if (dots == 2) {
//                break;
//            }
//            if (currentChar == '.') {
//                dots += 1;
//            }
//        }
//        return stringBuilder.toString();
//    }
    
    public String getVersion() {
        return version;
    }
    
    public void setVersion(String version) {
        this.version = version;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public String getApiVersion() {
        return apiVersion;
    }
    
    public void setApiVersion(String apiVersion) {
        this.apiVersion = apiVersion;
    }
    
    public String getAppVersion() {
        return appVersion;
    }
    
    public void setAppVersion(String appVersion) {
        this.appVersion = appVersion;
    }
    
    public List<String> getUrls() {
        return urls;
    }
    
    public void setUrls(List<String> urls) {
        this.urls = urls;
    }
    
    public Date getCreated() {
        return created;
    }
    
    public void setCreated(Date created) {
        this.created = created;
    }
    
    public String getDigest() {
        return digest;
    }
    
    public void setDigest(String digest) {
        this.digest = digest;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Chart chart = (Chart) o;
        return Objects.equals(name, chart.name) && Objects.equals(version, chart.version) && Objects.equals(description, chart.description) && Objects.equals(apiVersion, chart.apiVersion) && Objects.equals(appVersion, chart.appVersion) && Objects.equals(urls, chart.urls) && Objects.equals(created, chart.created) && Objects.equals(digest, chart.digest);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(name, version, description, apiVersion, appVersion, urls, created, digest);
    }
    
    @Override
    public String toString() {
        return "Chart{" +
                "name='" + name + '\'' +
                ", version='" + version + '\'' +
                ", description='" + description + '\'' +
                ", apiVersion='" + apiVersion + '\'' +
                ", appVersion='" + appVersion + '\'' +
                ", urls=" + urls +
                ", created=" + created +
                ", digest='" + digest + '\'' +
                '}';
    }
}
