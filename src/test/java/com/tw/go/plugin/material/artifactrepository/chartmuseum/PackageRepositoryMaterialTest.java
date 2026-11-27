package com.tw.go.plugin.material.artifactrepository.chartmuseum;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.thoughtworks.go.plugin.api.request.DefaultGoPluginApiRequest;
import com.thoughtworks.go.plugin.api.response.GoPluginApiResponse;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static com.tw.go.plugin.material.artifactrepository.chartmuseum.ChartMuseumFixture.CHART;
import static com.tw.go.plugin.material.artifactrepository.chartmuseum.ChartMuseumFixture.NEWEST;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Drives the plugin through handle() with the JSON GoCD sends, so these tests cover the
 * wire contract as well as the poller behind it.
 */
class PackageRepositoryMaterialTest {

    private static final Gson GSON = new Gson();
    private final PackageRepositoryMaterial plugin = new PackageRepositoryMaterial();

    @Test
    void fromAndToAreOptionalInThePackageForm() {
        JsonObject config = json(call("package-configuration", null));
        assertFalse(config.getAsJsonObject("FROM").get("required").getAsBoolean());
        assertFalse(config.getAsJsonObject("TO").get("required").getAsBoolean());
        assertTrue(config.getAsJsonObject("TO").get("display-name").getAsString().contains("<"));
        assertTrue(config.getAsJsonObject("PACKAGE_NAME").get("required").getAsBoolean());
    }

    @Test
    void packageSavedByPlugin11WithoutFromAndToIsValid() {
        GoPluginApiResponse response = call("validate-package-configuration", body("package-configuration", pkg(CHART, null, null)));
        assertEquals(200, response.responseCode());
        assertEquals("", response.responseBody());
    }

    @Test
    void emptyVersionWindowIsRejectedByValidation() {
        GoPluginApiResponse response = call("validate-package-configuration", body("package-configuration", pkg(CHART, "2.0.0", "2.0.0")));
        assertTrue(response.responseBody().contains("\"key\":\"TO\""), response.responseBody());
    }

    @Test
    void repositoryUrlMustBeHttp() {
        GoPluginApiResponse response = call("check-repository-connection", body("repository-configuration", repo("localhost:8080")));
        JsonObject result = json(response);
        assertEquals("FAILURE", result.get("status").getAsString());
        assertTrue(result.get("messages").toString().contains("http://"), result.toString());
    }

    @Test
    void repositoryConnectionSucceeds() {
        JsonObject result = json(call("check-repository-connection", body("repository-configuration", repo(ChartMuseumFixture.url()))));
        assertEquals("SUCCESS", result.get("status").getAsString());
    }

    @Test
    void latestRevisionWithPlugin11Config() {
        GoPluginApiResponse response = call("latest-revision", revisionRequest(pkg(CHART, null, null), null));
        assertEquals(200, response.responseCode(), response.responseBody());
        JsonObject revision = json(response);
        assertEquals(NEWEST, revision.get("revision").getAsString());
        assertEquals(NEWEST, revision.getAsJsonObject("data").get("VERSION").getAsString());
        assertEquals(ChartMuseumFixture.url() + "/charts/guestbook-" + NEWEST + ".tgz", revision.getAsJsonObject("data").get("LOCATION").getAsString());
        assertEquals(ChartMuseumFixture.url() + "/api/charts/guestbook/" + NEWEST, revision.get("trackbackUrl").getAsString());
        assertTrue(revision.get("timestamp").getAsString().matches("\\d{4}-\\d\\d-\\d\\dT\\d\\d:\\d\\d:\\d\\d\\.\\d{3}Z"), revision.toString());
    }

    @Test
    void latestRevisionWithEmptyWindowIsAnErrorThatSaysWhy() {
        GoPluginApiResponse response = call("latest-revision", revisionRequest(pkg(CHART, "100.0.0", null), null));
        assertEquals(500, response.responseCode());
        assertTrue(response.responseBody().contains("has no version matching >= 100.0.0"), response.responseBody());
    }

    @Test
    void latestRevisionSinceWithEmptyWindowMeansNoNewRevision() {
        GoPluginApiResponse response = call("latest-revision-since", revisionRequest(pkg(CHART, "100.0.0", null), "1.0.0"));
        assertEquals(200, response.responseCode(), response.responseBody());
        assertNull(response.responseBody());
    }

    @Test
    void latestRevisionSinceTheNewestIsNothing() {
        GoPluginApiResponse response = call("latest-revision-since", revisionRequest(pkg(CHART, "", ""), NEWEST));
        assertEquals(200, response.responseCode());
        assertNull(response.responseBody());
    }

    @Test
    void latestRevisionSinceAnOlderOneReturnsTheNewestInWindow() {
        GoPluginApiResponse response = call("latest-revision-since", revisionRequest(pkg(CHART, "1.1.0", "1.1.4"), "1.1.1-930314778"));
        assertEquals("1.1.3-732987498", json(response).get("revision").getAsString());
    }

    @Test
    void checkPackageConnectionReportsAnEmptyWindow() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("repository-configuration", repo(ChartMuseumFixture.url()));
        request.put("package-configuration", pkg(CHART, "100.0.0", null));
        JsonObject result = json(call("check-package-connection", GSON.toJson(request)));
        assertEquals("FAILURE", result.get("status").getAsString());
        assertTrue(result.get("messages").toString().contains("no version matching"), result.toString());
    }

    @Test
    void missingChartNameIsAnErrorNotANullPointer() {
        Map<String, Object> pkg = new LinkedHashMap<>();
        GoPluginApiResponse response = call("latest-revision", revisionRequest(pkg, null));
        assertEquals(500, response.responseCode());
        assertTrue(response.responseBody().contains("Chart name is required"), response.responseBody());
    }

    @Test
    void unreachableChartMuseumIsAnError() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("repository-configuration", repo("http://127.0.0.1:1"));
        request.put("package-configuration", pkg(CHART, null, null));
        GoPluginApiResponse response = call("latest-revision", GSON.toJson(request));
        assertEquals(500, response.responseCode());
        assertTrue(response.responseBody().contains("Could not reach"), response.responseBody());
    }

    private GoPluginApiResponse call(String requestName, String body) {
        DefaultGoPluginApiRequest request = new DefaultGoPluginApiRequest("package-repository", "1.0", requestName);
        request.setRequestBody(body);
        return plugin.handle(request);
    }

    private static JsonObject json(GoPluginApiResponse response) {
        assertEquals(200, response.responseCode(), response.responseBody());
        return GSON.fromJson(response.responseBody(), JsonObject.class);
    }

    private static String revisionRequest(Map<String, Object> pkg, String previousRevision) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("repository-configuration", repo(ChartMuseumFixture.url()));
        request.put("package-configuration", pkg);
        if (previousRevision != null) {
            Map<String, Object> previous = new LinkedHashMap<>();
            previous.put("revision", previousRevision);
            previous.put("timestamp", "2023-09-25T22:38:48.000Z");
            previous.put("data", Map.of());
            request.put("previous-revision", previous);
        }
        return GSON.toJson(request);
    }

    private static String body(String key, Map<String, Object> config) {
        return GSON.toJson(Map.of(key, config));
    }

    private static Map<String, Object> repo(String url) {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put(Constants.REPO_URL, Map.of("value", url));
        return config;
    }

    /** A null bound leaves the key out entirely, as GoCD does for a package saved by plugin 1.1. */
    private static Map<String, Object> pkg(String chart, String from, String to) {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put(Constants.PACKAGE_NAME, Map.of("value", chart));
        if (from != null) {
            config.put(Constants.FROM, Map.of("value", from));
        }
        if (to != null) {
            config.put(Constants.TO, Map.of("value", to));
        }
        return config;
    }
}
