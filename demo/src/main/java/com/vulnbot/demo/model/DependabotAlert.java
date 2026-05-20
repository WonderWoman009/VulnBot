// src/main/java/com/vulnbot/model/DependabotAlert.java
package com.vulnbot.demo.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DependabotAlert {

    private int number;
    private String state;

    @JsonProperty("security_advisory")
    private SecurityAdvisory securityAdvisory;

    @JsonProperty("security_vulnerability")
    private SecurityVulnerability securityVulnerability;

    @JsonProperty("html_url")
    private String htmlUrl;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SecurityAdvisory {
        private String summary;
        private String severity;

        @JsonProperty("cve_id")
        private String cveId;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SecurityVulnerability {

        @JsonProperty("package")
        private Package pkg;

        @JsonProperty("vulnerable_version_range")
        private String vulnerableVersionRange;

        @JsonProperty("first_patched_version")
        private FirstPatchedVersion firstPatchedVersion;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Package {
        private String name;
        private String ecosystem;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class FirstPatchedVersion {
        private String identifier;
    }

    // Helper methods
    public String getPackageName() {
        return securityVulnerability != null && 
               securityVulnerability.getPkg() != null
            ? securityVulnerability.getPkg().getName()
            : "unknown";
    }

    public String getSeverity() {
        return securityAdvisory != null
            ? securityAdvisory.getSeverity()
            : "unknown";
    }

    public String getSafeVersion() {
        if (securityVulnerability != null &&
            securityVulnerability.getFirstPatchedVersion() != null) {
            return securityVulnerability.getFirstPatchedVersion().getIdentifier();
        }
        return "Check advisory";
    }
}