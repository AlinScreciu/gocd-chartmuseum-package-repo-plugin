package com.tw.go.plugin.material.artifactrepository.chartmuseum;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.apache.maven.artifact.versioning.ComparableVersion;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ChartmuseumClient {
    private final String url;
    Gson gson;


    public ChartmuseumClient(String url) {
        this.url = url;
        gson = new GsonBuilder().create();
    }

    public void checkRepoConnection() {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url + "/health")).build();
        throwIfStatusNotOk(client, request);
    }

    private void throwIfStatusNotOk(HttpClient client, HttpRequest request) {
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new RuntimeException(response.toString());
            }
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public void checkChartConnection(String chartName) {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url + "/api/charts/" + chartName)).method("HEAD", HttpRequest.BodyPublishers.noBody()).build();
        throwIfStatusNotOk(client, request);
    }

    public List<Chart> getAllChartVersions(String chartName, ComparableVersion from, ComparableVersion to) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url + "/api/charts/" + chartName)).build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        String body = response.body();
        List<Chart> charts = new ArrayList<>(gson.fromJson(body, new TypeToken<List<Chart>>() {
        }.getType()));

            return filterFromTo(charts, from, to);
    }

    public Chart getLatestRevision(String chartName, ComparableVersion from, ComparableVersion to) {
        try {
            List<Chart> charts = getAllChartVersions(chartName, from, to);

            charts.sort((l, r) -> {
                ComparableVersion comparableVersionL = new ComparableVersion(l.getVersion());
                ComparableVersion comparableVersionR = new ComparableVersion(r.getVersion());
                return comparableVersionR.compareTo(comparableVersionL);
            });
            return charts.get(0);
        } catch (IOException | InterruptedException e) {
            return null;
        }
    }

    private List<Chart> filterFromTo(List<Chart> charts, ComparableVersion from, ComparableVersion to) {
        return charts.stream().filter(chart -> {
            ComparableVersion version = new ComparableVersion(chart.getVersion());
            if (from != null) {
                return version.compareTo(from) >= 0;
            }
            if (to != null) {
                return version.compareTo(to) <= 0;
            }
            return true;

        }).collect(Collectors.toCollection(ArrayList::new));
    }
}
