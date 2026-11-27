package com.tw.go.plugin.material.artifactrepository.chartmuseum;

import org.apache.maven.artifact.versioning.ComparableVersion;

/**
 * The optional version window of a package: {@code from <= version < to}.
 * Either bound may be absent. Versions compare with Maven's ComparableVersion,
 * so 1.10.0 is newer than 1.9.0 and 2.0.0-rc1 is older than 2.0.0.
 */
public final class VersionRange {
    public static final VersionRange ALL = new VersionRange(null, null);

    private final ComparableVersion from;
    private final ComparableVersion to;

    public VersionRange(ComparableVersion from, ComparableVersion to) {
        this.from = from;
        this.to = to;
    }

    /** Blank or null strings mean "no bound". */
    public static VersionRange parse(String from, String to) {
        return new VersionRange(toVersion(from), toVersion(to));
    }

    private static ComparableVersion toVersion(String value) {
        return value == null || value.isBlank() ? null : new ComparableVersion(value.trim());
    }

    public boolean contains(String version) {
        ComparableVersion v = new ComparableVersion(version);
        return (from == null || v.compareTo(from) >= 0) && (to == null || v.compareTo(to) < 0);
    }

    /** False when both bounds are set and no version can satisfy them. */
    public boolean isSatisfiable() {
        return from == null || to == null || from.compareTo(to) < 0;
    }

    @Override
    public String toString() {
        if (from == null && to == null) {
            return "any version";
        }
        if (to == null) {
            return ">= " + from;
        }
        if (from == null) {
            return "< " + to;
        }
        return ">= " + from + " and < " + to;
    }
}
