package com.tw.go.plugin.material.artifactrepository.chartmuseum;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static com.tw.go.plugin.material.artifactrepository.chartmuseum.ChartMuseumFixture.CHART;
import static com.tw.go.plugin.material.artifactrepository.chartmuseum.ChartMuseumFixture.NEWEST;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChartmuseumClientTest {

    private final ChartmuseumClient client = new ChartmuseumClient(ChartMuseumFixture.url());

    @Test
    void validUrlConnectionShouldWork() throws IOException {
        client.checkRepoConnection();
    }

    @Test
    void trailingSlashInUrlIsIgnored() throws IOException {
        ChartmuseumClient withSlash = new ChartmuseumClient(ChartMuseumFixture.url() + "/");
        withSlash.checkRepoConnection();
        assertEquals(NEWEST, withSlash.getLatestRevision(CHART, VersionRange.ALL).orElseThrow().getVersion());
    }

    @Test
    void urlWithoutSchemeFails() {
        IOException e = assertThrows(IOException.class, () -> new ChartmuseumClient("localhost:7070").checkRepoConnection());
        assertTrue(e.getMessage().contains("localhost:7070"), e.getMessage());
    }

    @Test
    void unreachableServerFails() {
        IOException e = assertThrows(IOException.class, () -> new ChartmuseumClient("http://127.0.0.1:1").checkRepoConnection());
        assertTrue(e.getMessage().startsWith("Could not reach"), e.getMessage());
    }

    @Test
    void latestRevisionIsTheHighestChartVersion() throws IOException {
        assertEquals(NEWEST, client.getLatestRevision(CHART, VersionRange.ALL).orElseThrow().getVersion());
    }

    @Test
    void latestRevisionWithToSet() throws IOException {
        Chart latest = client.getLatestRevision(CHART, VersionRange.parse(null, "1.1.2")).orElseThrow();
        assertEquals("1.1.1-930314778", latest.getVersion());
    }

    @Test
    void latestRevisionWithFromAndToSet() throws IOException {
        Chart latest = client.getLatestRevision(CHART, VersionRange.parse("1.1.2", "1.1.4")).orElseThrow();
        assertEquals("1.1.3-732987498", latest.getVersion());
    }

    @Test
    void latestRevisionWithFromSet() throws IOException {
        Chart latest = client.getLatestRevision(CHART, VersionRange.parse("1.1.2", null)).orElseThrow();
        assertEquals(NEWEST, latest.getVersion());
    }

    @Test
    void rangeWithNoMatchingVersionIsEmpty() throws IOException {
        Optional<Chart> latest = client.getLatestRevision(CHART, VersionRange.parse("100.0.0", null));
        assertTrue(latest.isEmpty());
    }

    @Test
    void allVersionsAreFilteredByRange() throws IOException {
        List<Chart> charts = client.getAllChartVersions(CHART, VersionRange.parse("1.1.2", "1.1.4"));
        assertEquals(List.of("1.1.2-240098175", "1.1.2-396927370", "1.1.2-977658686", "1.1.3-374670652", "1.1.3-732987498"),
                charts.stream().map(Chart::getVersion).sorted().collect(java.util.stream.Collectors.toList()));
    }

    @Test
    void unknownChartFailsWithNotFound() {
        IOException e = assertThrows(IOException.class, () -> client.getLatestRevision("no-such-chart", VersionRange.ALL));
        assertTrue(e.getMessage().contains("not found"), e.getMessage());
    }

    @Test
    void downloadUrlResolvesRelativeUrlsAgainstTheServer() throws IOException {
        Chart chart = client.getLatestRevision(CHART, VersionRange.ALL).orElseThrow();
        assertEquals(ChartMuseumFixture.url() + "/charts/guestbook-" + NEWEST + ".tgz", client.downloadUrl(chart));
    }

    @Test
    void downloadUrlKeepsAbsoluteUrls() {
        Chart chart = new Chart("app", "1.0.0", null, null, null, List.of("https://charts.example.com/app-1.0.0.tgz"), null, null);
        assertEquals("https://charts.example.com/app-1.0.0.tgz", client.downloadUrl(chart));
    }
}
