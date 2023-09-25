package chartmuseum;

import com.tw.go.plugin.material.artifactrepository.chartmuseum.Chart;
import com.tw.go.plugin.material.artifactrepository.chartmuseum.ChartmuseumClient;
import com.tw.go.plugin.material.artifactrepository.chartmuseum.PackageConfig;
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
        
        chartMuseumContainer = new GenericContainer<>(CHARTMUSEUM_IMAGE).withExposedPorts(8080).withEnv(env).withFileSystemBind("src/test/resources/charts", "/charts", BindMode.READ_WRITE);
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
        List<Chart> charts = chartmuseumClient.getAllChartVersions("guestbook");
        PackageConfig packageConfig = new PackageConfig("guestbook", null, null);
        Chart latest = chartmuseumClient.getLatestRevision(packageConfig);
        charts.sort((l, r) -> {
            ComparableVersion comparableVersionL = new ComparableVersion(l.getAppVersion());
            ComparableVersion comparableVersionR = new ComparableVersion(r.getAppVersion());
            return comparableVersionR.compareTo(comparableVersionL);
        });
        String expected = charts.get(0).getAppVersion();
        assertEquals(expected, latest.getAppVersion());
    }
    
    @Test
    void getLatestRevisionGreaterBetweenShouldFail() throws IOException, InterruptedException {
        ChartmuseumClient chartmuseumClient = new ChartmuseumClient("http://localhost:" + chartMuseumPort);
        PackageConfig packageConfig = new PackageConfig("guestbook", "2.0.0", "2.4.0");
        Chart latest = chartmuseumClient.getLatestRevision(packageConfig);
        assertNull(latest);
    }
    
    @Test
    void getLatestRevisionSmallerThanShouldWork() throws IOException, InterruptedException {
        ChartmuseumClient chartmuseumClient = new ChartmuseumClient("http://localhost:" + chartMuseumPort);
        PackageConfig packageConfig = new PackageConfig("guestbook", null, "1.25.7");
        Chart latest = chartmuseumClient.getLatestRevision(packageConfig);
        assertEquals("1.10.100-996806615", latest.getAppVersion());
    }
    
    @Test
    void getLatestRevisionGreaterBetweenShouldWork() throws IOException, InterruptedException {
        ChartmuseumClient chartmuseumClient = new ChartmuseumClient("http://localhost:" + chartMuseumPort);
        PackageConfig packageConfig = new PackageConfig("guestbook", "1.5.98", "1.5.100");
        Chart latest = chartmuseumClient.getLatestRevision(packageConfig);
        System.out.println(latest.getVersion());
        assertEquals("1.5.99-857761640", latest.getVersion());
    }
    
    @Test
    void getLatestRevisionGreaterFromShouldWork() throws IOException, InterruptedException {
        ChartmuseumClient chartmuseumClient = new ChartmuseumClient("http://localhost:" + chartMuseumPort);
        PackageConfig packageConfig = new PackageConfig("guestbook", "2.6.3", null);
        Chart latest = chartmuseumClient.getLatestRevision(packageConfig);
        System.out.println(latest.getVersion());
        assertEquals("2.6.40-1238agd-SNAPSHOT", latest.getVersion());
    }
    
    
}