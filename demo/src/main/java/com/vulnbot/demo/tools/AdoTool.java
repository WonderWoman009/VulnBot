// src/main/java/com/vulnbot/tools/AdoTool.java
package com.vulnbot.demo.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vulnbot.demo.model.DependabotAlert;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Base64;
import java.util.Map;

@Service
public class AdoTool {

    private static final Logger log = LoggerFactory.getLogger(AdoTool.class);

    private final OkHttpClient client = new OkHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    private final String adoPat;
    private final String adoOrgUrl;
    private final String adoProject;
    private final String adoAreaPath;
    private final String adoFeatureId;

    private static final Map<String, Integer> PRIORITY_MAP = Map.of(
        "critical", 1,
        "high",     1,
        "medium",   2,
        "low",      3
    );

    public AdoTool() {
        this.adoPat       = getEnv("ADO_PAT");
        this.adoOrgUrl    = getEnv("ADO_ORG_URL");
        this.adoProject   = getEnv("ADO_PROJECT");
        this.adoAreaPath  = getEnv("ADO_AREA_PATH");
        this.adoFeatureId = getEnv("ADO_FEATURE_ID");
    }

    public String createWorkItem(DependabotAlert alert, String fixSuggestion) throws IOException {

        // Check duplicate first
        if (workItemExists(alert.getNumber())) {
            log.info("Story already exists for Alert #{} — skipping", 
                alert.getNumber());
            return "SKIPPED";
        }

        String url = String.format(
            "%s/%s/_apis/wit/workitems/$User%%20Story?api-version=7.1",
            adoOrgUrl, adoProject
        );

        String body = buildRequestBody(alert, fixSuggestion);

        Request request = new Request.Builder()
            .url(url)
            .header("Authorization", buildAuthHeader())
            .post(RequestBody.create(body,
                MediaType.parse("application/json-patch+json")))
            .build();

        try (Response response = client.newCall(request).execute()) {

            String responseBody = response.body().string();
            
            if (!response.isSuccessful()) {
                log.error("ADO API error: {} {}", response.code(), response.message());
                log.error("ADO Error Detail: {}", responseBody); // ← Add this
                return "FAILED";
            }
            // rest of code
        }

        return "FAILED";
        // try (Response response = client.newCall(request).execute()) {

        //     if (!response.isSuccessful()) {
        //         log.error("ADO API error: {} {}", 
        //             response.code(), response.message());
        //         return "FAILED";
        //     }

        //     JsonNode node = mapper.readTree(response.body().string());
        //     String workItemId = node.path("id").asText();

        //     log.info("✅ Created ADO story #{} for Alert #{} [{}] {}",
        //         workItemId,
        //         alert.getNumber(),
        //         alert.getSeverity().toUpperCase(),
        //         alert.getPackageName()
        //     );

        //     return workItemId;

        // } catch (Exception e) {
        //     log.error("Failed to create ADO story for Alert #{}: {}",
        //         alert.getNumber(), e.getMessage());
        //     return "FAILED";
        // }
    }

    private boolean workItemExists(int alertNumber) {
        String url = String.format(
            "%s/%s/_apis/wit/wiql?api-version=7.1",
            adoOrgUrl, adoProject
        );

        String query = String.format(
            "{\"query\": \"SELECT [Id] FROM WorkItems WHERE " +
            "[System.Title] CONTAINS 'VulnBot Alert #%d' " +
            "AND [System.State] <> 'Closed'\"}",
            alertNumber
        );

        Request request = new Request.Builder()
            .url(url)
            .header("Authorization", buildAuthHeader())
            .header("Content-Type", "application/json")
            .post(RequestBody.create(query,
                MediaType.parse("application/json")))
            .build();

        try (Response response = client.newCall(request).execute()) {
            String body = response.body().string();
            JsonNode node = mapper.readTree(body);
            return node.path("workItems").size() > 0;
        } catch (Exception e) {
            log.warn("Duplicate check failed — allowing creation");
            return false;
        }
    }

    private String buildRequestBody(
        DependabotAlert alert, String fixSuggestion) {

        try {
            String severity = alert.getSeverity().toUpperCase();
            int priority = PRIORITY_MAP.getOrDefault(
                alert.getSeverity().toLowerCase(), 2);

            String title = String.format(
                "[VulnBot] Alert #%d [%s] %s",
                alert.getNumber(),
                severity,
                alert.getPackageName()
            );

            String description = String.format(
                "Auto-created by VulnBot. "
                + "Package: %s. "
                + "Severity: %s. "
                + "CVE: %s. "
                + "Vulnerable Range: %s. "
                + "Safe Version: %s. "
                + "GitHub Alert: %s. "
                + "Fix: %s",
                alert.getPackageName(),
                severity,
                alert.getSecurityAdvisory().getCveId() != null
                    ? alert.getSecurityAdvisory().getCveId() : "N/A",
                alert.getSecurityVulnerability().getVulnerableVersionRange(),
                alert.getSafeVersion(),
                alert.getHtmlUrl(),
                fixSuggestion != null ? fixSuggestion : "Phase 2 pending"
            );

            // Build using Jackson — safe, no manual escaping needed
            ArrayNode patchDocument = mapper.createArrayNode();

            // Title
            patchDocument.add(mapper.createObjectNode()
                .put("op", "add")
                .put("path", "/fields/System.Title")
                .put("value", title));

            // Description — plain text for now, no HTML
            patchDocument.add(mapper.createObjectNode()
                .put("op", "add")
                .put("path", "/fields/System.Description")
                .put("value", description));

            // Area Path
            patchDocument.add(mapper.createObjectNode()
                .put("op", "add")
                .put("path", "/fields/System.AreaPath")
                .put("value", adoAreaPath));

            // Priority
            patchDocument.add(mapper.createObjectNode()
                .put("op", "add")
                .put("path", "/fields/Microsoft.VSTS.Common.Priority")
                .put("value", priority));

            // Tags
            patchDocument.add(mapper.createObjectNode()
                .put("op", "add")
                .put("path", "/fields/System.Tags")
                .put("value", "VulnBot; Security; " + severity));

            // Link to Feature
            ObjectNode relationValue = mapper.createObjectNode();
            relationValue.put("rel", "System.LinkTypes.Hierarchy-Reverse");
            relationValue.put("url",
                adoOrgUrl + "/_apis/wit/workitems/" + adoFeatureId);
            relationValue.set("attributes",
                mapper.createObjectNode()
                    .put("comment", "Auto-linked by VulnBot"));

            patchDocument.add(mapper.createObjectNode()
                .put("op", "add")
                .put("path", "/relations/-")
                .set("value", relationValue));

            return mapper.writeValueAsString(patchDocument);

        } catch (Exception e) {
            log.error("Failed to build request body: {}", e.getMessage());
            return "[]";
        }
    }

    private String buildAuthHeader() {
        String encoded = Base64.getEncoder()
            .encodeToString((":" + adoPat).getBytes());
        return "Basic " + encoded;
    }

    private String escape(String value) {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", " ")
            .replace("\r", "");
    }

    private String getEnv(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                "Missing required environment variable: " + key
            );
        }
        return value;
    }
}