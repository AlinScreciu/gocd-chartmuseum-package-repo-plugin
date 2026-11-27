package com.tw.go.plugin.material.artifactrepository.chartmuseum;

import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.time.Duration;

/**
 * One ChartMuseum container per test JVM, serving the ~4,750 guestbook charts in
 * src/test/resources/charts. The folder is mounted read-only and state files are off,
 * so a test run leaves the working tree untouched.
 */
final class ChartMuseumFixture {
    static final String IMAGE = "ghcr.io/helm/chartmuseum:v0.16.3";
    static final String CHART = "guestbook";
    /** Newest version among the fixtures. */
    static final String NEWEST = "2.6.40-1238agd-SNAPSHOT";

    private static GenericContainer<?> container;

    private ChartMuseumFixture() {
    }

    static synchronized String url() {
        if (container == null) {
            GenericContainer<?> c = new GenericContainer<>(IMAGE)
                    .withExposedPorts(8080)
                    .withEnv("STORAGE", "local")
                    .withEnv("STORAGE_LOCAL_ROOTDIR", "/charts")
                    .withEnv("DISABLE_STATEFILES", "true")
                    .withFileSystemBind("src/test/resources/charts", "/charts", BindMode.READ_ONLY)
                    .waitingFor(Wait.forHttp("/health").forStatusCode(200).withStartupTimeout(Duration.ofMinutes(2)));
            c.start();
            container = c;
        }
        return "http://" + container.getHost() + ":" + container.getMappedPort(8080);
    }
}
