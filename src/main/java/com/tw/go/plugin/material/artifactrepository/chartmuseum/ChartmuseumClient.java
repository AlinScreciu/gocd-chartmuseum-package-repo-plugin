package com.tw.go.plugin.material.artifactrepository.chartmuseum;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import org.apache.maven.artifact.versioning.ComparableVersion;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Talks to the ChartMuseum HTTP API. Every failure (unreachable server, non-2xx status,
 * unparseable body) is thrown as an IOException so GoCD reports it as a failed material check.
 */
public class ChartmuseumClient {
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);

    private final String baseUrl;
    private final HttpClient httpClient;
    private final Gson gson = new Gson();

    public ChartmuseumClient(String url) {
        this.baseUrl = stripTrailingSlashes(url);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void checkRepoConnection() throws IOException {
        get("/health");
    }

    /** All versions of the chart that fall inside the range. Throws if the chart does not exist. */
    public List<Chart> getAllChartVersions(String chartName, VersionRange range) throws IOException {
        HttpResponse<String> response = send("/api/charts/" + chartName);
        if (response.statusCode() == 404) {
            throw new IOException(String.format("Chart '%s' not found in ChartMuseum at %s", chartName, baseUrl));
        }
        requireSuccess(response);
        List<Chart> charts;
        try {
            charts = gson.fromJson(response.body(), new TypeToken<List<Chart>>() {
            }.getType());
        } catch (JsonParseException e) {
            throw new IOException("Unexpected response from " + response.uri() + ": " + e.getMessage(), e);
        }
        if (charts == null) {
            return new ArrayList<>();
        }
        return charts.stream()
                .filter(chart -> chart.getVersion() != null && range.contains(chart.getVersion()))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    /** The newest version inside the range, or empty if the chart exists but no version matches. */
    public Optional<Chart> getLatestRevision(String chartName, VersionRange range) throws IOException {
        return getAllChartVersions(chartName, range).stream()
                .max(Comparator.comparing(chart -> new ComparableVersion(chart.getVersion())));
    }

    /** Download URL of the chart archive, as ChartMuseum lists it in "urls". */
    public String downloadUrl(Chart chart) {
        List<String> urls = chart.getUrls();
        if (urls == null || urls.isEmpty() || urls.get(0).isBlank()) {
            return baseUrl + "/charts/" + chart.getName() + "-" + chart.getVersion() + ".tgz";
        }
        String first = urls.get(0);
        if (first.startsWith("http://") || first.startsWith("https://")) {
            // ChartMuseum was started with --chart-url
            return first;
        }
        return baseUrl + "/" + first.replaceFirst("^/+", "");
    }

    /** API page describing one chart version; GoCD links to it from the material revision. */
    public String chartVersionUrl(Chart chart) {
        return baseUrl + "/api/charts/" + chart.getName() + "/" + chart.getVersion();
    }

    private void get(String path) throws IOException {
        requireSuccess(send(path));
    }

    private HttpResponse<String> send(String path) throws IOException {
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Accept", "application/json")
                    .GET()
                    .build();
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid ChartMuseum URL '" + baseUrl + "': " + e.getMessage(), e);
        }
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while calling " + request.uri(), e);
        } catch (IOException e) {
            throw new IOException("Could not reach " + request.uri() + ": " + e, e);
        }
    }

    private static void requireSuccess(HttpResponse<String> response) throws IOException {
        int status = response.statusCode();
        if (status < 200 || status >= 300) {
            throw new IOException("ChartMuseum returned HTTP " + status + " for " + response.uri());
        }
    }

    private static String stripTrailingSlashes(String url) {
        String trimmed = url == null ? "" : url.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}
