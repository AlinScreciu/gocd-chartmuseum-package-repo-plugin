package com.tw.go.plugin.material.artifactrepository.chartmuseum;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VersionRangeTest {

    @Test
    void lowerBoundIsInclusiveUpperBoundIsExclusive() {
        VersionRange range = VersionRange.parse("1.2.0", "2.0.0");
        assertTrue(range.contains("1.2.0"));
        assertTrue(range.contains("1.10.3"));
        assertTrue(range.contains("1.99.0"));
        assertFalse(range.contains("1.1.9"));
        assertFalse(range.contains("2.0.0"), "the label says '<', so 2.0.0 itself is out");
        assertFalse(range.contains("2.0.1"));
    }

    @Test
    void preReleasesOfTheUpperBoundAreBelowIt() {
        // Maven ordering: 2.0.0-rc1 and 2.0.0-SNAPSHOT come before 2.0.0.
        VersionRange range = VersionRange.parse(null, "2.0.0");
        assertTrue(range.contains("2.0.0-rc1"));
        assertTrue(range.contains("2.0.0-SNAPSHOT"));
    }

    @Test
    void numericBuildSuffixSortsAfterTheBareVersion() {
        // The fixtures use versions like 1.1.2-240098175; Maven treats them as newer than 1.1.2.
        assertFalse(VersionRange.parse(null, "1.1.2").contains("1.1.2-240098175"));
        assertTrue(VersionRange.parse("1.1.2", null).contains("1.1.2-240098175"));
    }

    @Test
    void blankBoundsMeanNoBound() {
        VersionRange range = VersionRange.parse(" ", "");
        assertTrue(range.contains("0.0.1"));
        assertTrue(range.contains("99.0.0"));
        assertEquals("any version", range.toString());
    }

    @Test
    void emptyOrInvertedRangeIsNotSatisfiable() {
        assertFalse(VersionRange.parse("2.0.0", "2.0.0").isSatisfiable());
        assertFalse(VersionRange.parse("2.0.0", "1.0.0").isSatisfiable());
        assertTrue(VersionRange.parse("1.0.0", "1.0.1").isSatisfiable());
        assertTrue(VersionRange.parse("1.0.0", null).isSatisfiable());
    }
}
