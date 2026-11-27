package com.tw.go.plugin.material.artifactrepository.chartmuseum;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.MountableFile;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * End-to-end check against a real GoCD server: install the built plugin jar, configure a
 * ChartMuseum repository and two packages through the GoCD API, and wait for pipelines to
 * trigger on the chart versions the plugin reports.
 *
 * <pre>mvn verify -Pgocd-compat -Dgocd.version=23.1.0</pre>
 *
 * Covers: the plugin loads (target-go-version, Java level, bundled lib/), latest-revision on a
 * new material, latest-revision-since after a new chart is uploaded, a package without FROM/TO
 * (as saved by plugin 1.1), and a version window that must ignore the upload.
 */
class GocdCompatibilityIT {
    private static final String GOCD_VERSION = System.getProperty("gocd.version", "26.2.0");
    private static final Path PLUGIN_JAR = Path.of(System.getProperty("plugin.jar", "target/chartmuseum.material.poller.jar"));
    private static final Path CHARTS = Path.of("src/test/resources/charts");
    private static final String API = "application/vnd.go.cd+json";
    private static final Gson GSON = new Gson();

    private static Network network;
    private static GenericContainer<?> chartmuseum;
    private static GenericContainer<?> gocd;
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    @BeforeAll
    static void start() throws Exception {
        assertTrue(Files.isRegularFile(PLUGIN_JAR), "Build the plugin first: " + PLUGIN_JAR);
        network = Network.newNetwork();

        chartmuseum = new GenericContainer<>(ChartMuseumFixture.IMAGE)
                .withNetwork(network)
                .withNetworkAliases("chartmuseum")
                .withExposedPorts(8080)
                .withEnv("STORAGE", "local")
                .withEnv("STORAGE_LOCAL_ROOTDIR", "/tmp/charts")
                .waitingFor(Wait.forHttp("/health").forStatusCode(200));
        chartmuseum.start();
        upload("guestbook-1.1.1-930314778.tgz");
        upload("guestbook-1.1.3-732987498.tgz");

        gocd = new GenericContainer<>("gocd/gocd-server:v" + GOCD_VERSION)
                .withNetwork(network)
                .withExposedPorts(8153)
                .withCopyFileToContainer(MountableFile.forHostPath(PLUGIN_JAR, 0644), "/tmp/chartmuseum.jar")
                // The image's install-gocd-plugins script fetches this with curl into /godata/plugins/external.
                .withEnv("GOCD_PLUGIN_INSTALL_chartmuseum", "file:///tmp/chartmuseum.jar")
                .withEnv("GOCD_SERVER_JVM_OPTS", "-Dcruise.material.update.interval=5000 -Dmaterial.update.idle.interval=5000 -Dcruise.produce.build.cause.interval=2000")
                .waitingFor(Wait.forHttp("/go/api/v1/health").forStatusCode(200).withStartupTimeout(Duration.ofMinutes(15)));
        gocd.start();
    }

    @AfterAll
    static void stop() {
        if (gocd != null) {
            String logs = gocd.getLogs();
            System.out.println(logs.substring(Math.max(0, logs.length() - 20_000)));
            gocd.stop();
        }
        if (chartmuseum != null) {
            chartmuseum.stop();
        }
        if (network != null) {
            network.close();
        }
    }

    @Test
    void pluginPollsChartMuseumOnThisGocdVersion() throws Exception {
        JsonObject pluginInfo = waitFor("plugin chartmuseum-repo to load", () -> {
            HttpResponse<String> r = gocdCall("GET", "/go/api/admin/plugin_info/chartmuseum-repo", null);
            return r.statusCode() == 200 ? GSON.fromJson(r.body(), JsonObject.class) : null;
        });
        assertEquals("active", pluginInfo.getAsJsonObject("status").get("state").getAsString(), pluginInfo.toString());

        created(gocdCall("POST", "/go/api/admin/repositories", Map.of(
                "repo_id", "chartmuseum",
                "name", "chartmuseum",
                "plugin_metadata", Map.of("id", "chartmuseum-repo", "version", "1"),
                "configuration", List.of(Map.of("key", "REPO_URL", "value", "http://chartmuseum:8080")))));

        // No FROM/TO keys at all, like a package saved by plugin 1.1.
        createPackage("guestbook-all", List.of(Map.of("key", "PACKAGE_NAME", "value", "guestbook")));
        createPackage("guestbook-1-1-1", List.of(
                Map.of("key", "PACKAGE_NAME", "value", "guestbook"),
                Map.of("key", "FROM", "value", "1.1.0"),
                Map.of("key", "TO", "value", "1.1.2")));

        createPipeline("follow-all", "guestbook-all");
        createPipeline("follow-window", "guestbook-1-1-1");

        waitForRevisions("follow-all", List.of("1.1.3-732987498"));
        waitForRevisions("follow-window", List.of("1.1.1-930314778"));
        // There is no agent, so each first run waits forever, and GoCD does not schedule a
        // pipeline again while its first stage is still active. Cancel the runs to unblock them.
        cancelFirstRun("follow-all");
        cancelFirstRun("follow-window");

        upload("guestbook-" + ChartMuseumFixture.NEWEST + ".tgz");
        waitForRevisions("follow-all", List.of(ChartMuseumFixture.NEWEST, "1.1.3-732987498"));

        // Give the windowed material more than one poll cycle (GoCD's default is 60 s) to (wrongly) pick up the upload.
        Thread.sleep(75_000);
        assertEquals(List.of("1.1.1-930314778"), revisions("follow-window"));
    }

    private static void createPackage(String id, List<Map<String, String>> configuration) throws Exception {
        created(gocdCall("POST", "/go/api/admin/packages", Map.of(
                "id", id,
                "name", id,
                "auto_update", true,
                "package_repo", Map.of("id", "chartmuseum"),
                "configuration", configuration)));
    }

    private static void createPipeline(String name, String packageId) throws Exception {
        Map<String, Object> job = Map.of("name", "noop", "tasks", List.of(Map.of("type", "exec", "attributes", Map.of("command", "true"))));
        Map<String, Object> pipeline = Map.of(
                "name", name,
                "materials", List.of(Map.of("type", "package", "attributes", Map.of("ref", packageId))),
                "stages", List.of(Map.of("name", "deploy", "jobs", List.of(job))));
        created(gocdCall("POST", "/go/api/admin/pipelines", Map.of("group", "compat", "pipeline", pipeline)));
        // Pipelines created through the API start paused on some GoCD versions.
        HttpResponse<String> unpause = gocdCall("POST", "/go/api/pipelines/" + name + "/unpause", null);
        assertTrue(unpause.statusCode() == 200 || unpause.statusCode() == 409, "unpause " + name + ": " + unpause.statusCode() + " " + unpause.body());
    }

    private static void cancelFirstRun(String pipeline) throws Exception {
        waitFor("run 1 of " + pipeline + " to cancel", () -> {
            HttpResponse<String> r = gocdCall("POST", "/go/api/stages/" + pipeline + "/1/deploy/1/cancel", null);
            return r.statusCode() == 200 ? Boolean.TRUE : null;
        });
    }

    private static void waitForRevisions(String pipeline, List<String> expected) throws Exception {
        waitFor(pipeline + " to run for " + expected, () -> expected.equals(revisions(pipeline)) ? Boolean.TRUE : null);
    }

    /** Package revision of each run of the pipeline, newest first. */
    private static List<String> revisions(String pipeline) throws IOException, InterruptedException {
        HttpResponse<String> r = gocdCall("GET", "/go/api/pipelines/" + pipeline + "/history", null);
        List<String> result = new ArrayList<>();
        if (r.statusCode() != 200) {
            return result;
        }
        JsonArray runs = GSON.fromJson(r.body(), JsonObject.class).getAsJsonArray("pipelines");
        for (JsonElement run : runs) {
            JsonArray materials = run.getAsJsonObject().getAsJsonObject("build_cause").getAsJsonArray("material_revisions");
            result.add(materials.get(0).getAsJsonObject().getAsJsonArray("modifications").get(0).getAsJsonObject().get("revision").getAsString());
        }
        return result;
    }

    private static void created(HttpResponse<String> response) {
        assertTrue(response.statusCode() == 200 || response.statusCode() == 201,
                response.request().method() + " " + response.request().uri() + " -> " + response.statusCode() + " " + response.body());
    }

    private static HttpResponse<String> gocdCall(String method, String path, Object body) throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://" + gocd.getHost() + ":" + gocd.getMappedPort(8153) + path))
                .timeout(Duration.ofSeconds(60))
                .header("Accept", API)
                .header("X-GoCD-Confirm", "true");
        if (body == null) {
            request.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            request.header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(GSON.toJson(body)));
        }
        return HTTP.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static void upload(String chartFile) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://" + chartmuseum.getHost() + ":" + chartmuseum.getMappedPort(8080) + "/api/charts"))
                .header("Content-Type", "application/octet-stream")
                .POST(HttpRequest.BodyPublishers.ofFile(CHARTS.resolve(chartFile)))
                .build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(201, response.statusCode(), "upload " + chartFile + ": " + response.body());
    }

    interface Check<T> {
        T get() throws Exception;
    }

    private static <T> T waitFor(String what, Check<T> check) throws Exception {
        Instant deadline = Instant.now().plus(Duration.ofMinutes(5));
        Exception last = null;
        while (Instant.now().isBefore(deadline)) {
            try {
                T value = check.get();
                if (value != null) {
                    return value;
                }
            } catch (Exception e) {
                last = e;
            }
            Thread.sleep(2_000);
        }
        fail("Timed out waiting for " + what + (last == null ? "" : ": " + last));
        return null;
    }
}
