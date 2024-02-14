package com.tw.go.plugin.material.artifactrepository.chartmuseum;

import org.apache.maven.artifact.versioning.ComparableVersion;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ChartmuseumClientTest {
    static private final String CHARTMUSEUM_IMAGE = "ghcr.io/helm/chartmuseum:v0.14.0";
    static GenericContainer<?> chartMuseumContainer;
    static private String chartMuseumPort;

    @BeforeAll
    static void setupChartmuseumContainer() {

        Map<String, String> env = new HashMap<>();
        env.put("DEBUG", "1");
        env.put("STORAGE", "LOCAL");
        env.put("STORAGE_LOCAL_ROOTDIR", "/charts");

        chartMuseumContainer = new GenericContainer<>(CHARTMUSEUM_IMAGE).withExposedPorts(8080).withEnv(env)
                .withFileSystemBind("src/test/resources/charts", "/charts", BindMode.READ_WRITE);
        chartMuseumContainer.start();
        chartMuseumPort = chartMuseumContainer.getMappedPort(8080).toString();
    }

    @AfterAll
    static void cleanup() {
        if (!chartMuseumContainer.isRunning()) {
            return;
        }
        System.out.println(chartMuseumContainer.getLogs());

        chartMuseumContainer.stop();
    }

    @Test
    void validUrlConnectionShouldWork() {
        ChartmuseumClient chartmuseumClient = new ChartmuseumClient("http://localhost:" + chartMuseumPort);
        chartmuseumClient.checkRepoConnection();
    }

    @Test
    void invalidUrlConnectionShouldFail() {
        ChartmuseumClient chartmuseumClient = new ChartmuseumClient("localhost:7070");
        try {
            chartmuseumClient.checkRepoConnection();
            fail();
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("invalid"));
        }
    }

    @Test
    void getLatestRevision() throws IOException, InterruptedException {
        ChartmuseumClient chartmuseumClient = new ChartmuseumClient("http://localhost:" + chartMuseumPort);
        List<Chart> charts = chartmuseumClient.getAllChartVersions("guestbook", null, null);
        Chart latest = chartmuseumClient.getLatestRevision("guestbook", null, null);
        charts.sort((l, r) -> {
            ComparableVersion comparableVersionL = new ComparableVersion(l.getAppVersion());
            ComparableVersion comparableVersionR = new ComparableVersion(r.getAppVersion());
            return comparableVersionR.compareTo(comparableVersionL);
        });
        String expected = charts.get(0).getAppVersion();
        assertEquals(expected, latest.getAppVersion());
    }


    @Test
    void getLatestRevisionWithToSet() {
        ChartmuseumClient chartmuseumClient = new ChartmuseumClient("http://localhost:" + chartMuseumPort);
        Chart latest = chartmuseumClient.getLatestRevision("guestbook", null, new ComparableVersion("1.1.2"));
        assertNotNull(latest);
        assertEquals("1.1.1-930314778", latest.getVersion());
        // Add assertions to verify the correctness of the returned latest revision
    }

    @Test
    void getLatestRevisionWithFromSetAndToSet() {
        ChartmuseumClient chartmuseumClient = new ChartmuseumClient("http://localhost:" + chartMuseumPort);
        Chart latest = chartmuseumClient.getLatestRevision("guestbook", new ComparableVersion("1.1.2"), new ComparableVersion("1.1.4"));
        assertNotNull(latest);
        assertEquals("1.1.3-732987498", latest.getVersion());
        // Add assertions to verify the correctness of the returned latest revision
    }

    @Test
    void getLatestRevisionWithFromSet() {
        ChartmuseumClient chartmuseumClient = new ChartmuseumClient("http://localhost:" + chartMuseumPort);
        Chart latest = chartmuseumClient.getLatestRevision("guestbook", new ComparableVersion("1.1.2"),null);
        assertNotNull(latest);
        assertEquals("2.6.40-1238agd-SNAPSHOT", latest.getVersion());
        // Add assertions to verify the correctness of the returned latest revision
    }
}