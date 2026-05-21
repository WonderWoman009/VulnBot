// tools/AdoTool.java
package com.vulnbot.demo.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vulnbot.demo.config.TeamConfig;
import com.vulnbot.demo.model.GroupedAlert;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Base64;
import java.util.Map;

@Service
public class AdoTool {

    private static final Logger log = LoggerFactory.getLogger(AdoTool.class);

    private final OkHttpClient client = new OkHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${ado.pat}")
    private String adoPat;

    @Value("${ado.org.url}")
    private String adoOrgUrl;

    private static final Map<String, Integer> PRIORITY_MAP = Map.of(
        "critical", 1,
        "high",     1,
        "medium",   2,
        "low",      3
    );

    /**
     * Create ADO story for a grouped alert under the team's area path
     * Each team has its own area path and feature ID from config.json
     */
    public String createWorkItem(GroupedAlert alert, TeamConfig team) {

        log.info("Attempting to create story for [{}] {} — Team: {}",
            alert.getSeverity().toUpperCase(),
            alert.getPackageName(),
            team.getTeamName()
        );
    
        // Single duplicate check
        if (workItemExists(alert, team)) {
            log.info("Story already exists for [{}] {} in team {} — skipping",
                alert.getSeverity().toUpperCase(),
                alert.getPackageName(),
                team.getTeamName());
            return "SKIPPED";
        }
    
        String url = String.format(
            "%s/%s/_apis/wit/workitems/$User%%20Story?api-version=7.1",
            adoOrgUrl, team.getAdoProject()
        );
    
        try {
            String body = buildRequestBody(alert, team);
    
            Request request = new Request.Builder()
                .url(url)
                .header("Authorization", buildAuthHeader())
                .post(RequestBody.create(body,
                    MediaType.parse("application/json-patch+json")))
                .build();
    
            try (Response response = client.newCall(request).execute()) {
                String responseBody = response.body().string();
    
                if (!response.isSuccessful()) {
                    log.error("ADO API error: {} {}",
                        response.code(), response.message());
                    log.error("ADO Error Detail: {}", responseBody);
                    return "FAILED";
                }
    
                String workItemId = mapper.readTree(responseBody)
                    .path("id").asText();
    
                log.info("✅ Created story #{} for [{}] {} — Repos: {} — Team: {}",
                    workItemId,
                    alert.getSeverity().toUpperCase(),
                    alert.getPackageName(),
                    alert.getAffectedReposSummary(),
                    team.getTeamName()
                );
    
                return workItemId;
            }
    
        } catch (Exception e) {
            log.error("Failed to create story for {} in team {}: {}",
                alert.getPackageName(), team.getTeamName(), e.getMessage());
            return "FAILED";
        }
    }
    
    private boolean workItemExists(GroupedAlert alert, TeamConfig team) {
        String url = String.format(
            "%s/%s/_apis/wit/wiql?api-version=7.1",
            adoOrgUrl, team.getAdoProject()
        );

        log.info("Checking duplicate — URL: {}", url); // ← Add this

        String query = String.format(
            "{\"query\": \"SELECT [Id] FROM WorkItems WHERE "
            + "[System.Title] CONTAINS 'VulnBot' "
            + "AND [System.Title] CONTAINS '%s' "
            + "AND [System.AreaPath] UNDER '%s' "
            + "AND [System.State] <> 'Closed'\"}",
            alert.getPackageName().replace("'", ""),
            team.getAdoAreaPath()
        );

        log.info("Duplicate check query: {}", query); // ← Add this
        try {
            Request request = new Request.Builder()
                .url(url)
                .header("Authorization", buildAuthHeader())
                .header("Content-Type", "application/json")
                .post(RequestBody.create(query,
                    MediaType.parse("application/json")))
                .build();
    
            try (Response response = client.newCall(request).execute()) {
                String body = response.body().string();
                log.info("Duplicate check response: {}", body); // ← Add this
                int count = mapper.readTree(body)
                    .path("workItems").size();
                log.info("Existing stories found: {}", count); // ← Add this
                return count > 0;
            }
        } catch (Exception e) {
            log.warn("Duplicate check failed: {}", e.getMessage()); // ← Improve this
            return false;
        }
    }

    private String buildRequestBody(
            GroupedAlert alert, TeamConfig team) throws Exception {

        String severity = alert.getSeverity().toUpperCase();
        int priority = PRIORITY_MAP.getOrDefault(
            alert.getSeverity().toLowerCase(), 2);

        // Title includes affected repos
        String title = String.format(
            "[VulnBot] [%s] %s — Repos: %s",
            severity,
            alert.getPackageName(),
            alert.getAffectedReposSummary()
        );

        // Description includes all affected repo links
        StringBuilder repoDetails = new StringBuilder();
        for (int i = 0; i < alert.getAffectedRepos().size(); i++) {
            repoDetails.append(String.format(
                "Repo: %s | Alert: %s. ",
                alert.getAffectedRepos().get(i),
                alert.getAlertUrls().get(i)
            ));
        }

        String description = String.format(
            "Auto-created by VulnBot. "
            + "Package: %s. "
            + "Severity: %s. "
            + "CVE: %s. "
            + "Vulnerable Range: %s. "
            + "Safe Version: %s. "
            + "Affected Repos: %s. "
            + "Repo Details: %s",
            alert.getPackageName(),
            severity,
            alert.getCveId() != null ? alert.getCveId() : "N/A",
            alert.getVulnerableRange(),
            alert.getSafeVersion(),
            alert.getAffectedReposSummary(),
            repoDetails
        );

        // Tags include team ID for duplicate detection
        String tags = String.format(
            "VulnBot; Security; %s; %s",
            severity, team.getTeamId()
        );

        ArrayNode patchDocument = mapper.createArrayNode();

        patchDocument.add(mapper.createObjectNode()
            .put("op", "add")
            .put("path", "/fields/System.Title")
            .put("value", title));

        patchDocument.add(mapper.createObjectNode()
            .put("op", "add")
            .put("path", "/fields/System.Description")
            .put("value", description));

        patchDocument.add(mapper.createObjectNode()
            .put("op", "add")
            .put("path", "/fields/System.AreaPath")
            .put("value", team.getAdoAreaPath()));

        patchDocument.add(mapper.createObjectNode()
            .put("op", "add")
            .put("path", "/fields/Microsoft.VSTS.Common.Priority")
            .put("value", priority));

        patchDocument.add(mapper.createObjectNode()
            .put("op", "add")
            .put("path", "/fields/System.Tags")
            .put("value", tags));

        // Link to team's Feature
        ObjectNode relationValue = mapper.createObjectNode();
        relationValue.put("rel", "System.LinkTypes.Hierarchy-Reverse");
        relationValue.put("url",
            adoOrgUrl + "/_apis/wit/workitems/" + team.getAdoFeatureId());
        relationValue.set("attributes",
            mapper.createObjectNode()
                .put("comment", "Auto-linked by VulnBot"));

        patchDocument.add(mapper.createObjectNode()
            .put("op", "add")
            .put("path", "/relations/-")
            .set("value", relationValue));

        return mapper.writeValueAsString(patchDocument);
    }

    private String buildAuthHeader() {
        String encoded = Base64.getEncoder()
            .encodeToString((":" + adoPat).getBytes());
        return "Basic " + encoded;
    }
}