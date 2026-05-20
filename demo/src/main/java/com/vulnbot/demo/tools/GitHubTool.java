// src/main/java/com/vulnbot/tools/GitHubTool.java
package com.vulnbot.demo.tools;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vulnbot.demo.model.DependabotAlert;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class GitHubTool {

    private static final Logger log = LoggerFactory.getLogger(GitHubTool.class);

    private final OkHttpClient client = new OkHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    private final String githubToken;
    private final String repoOwner;
    private final String repoName;

    // Severity priority map for sorting
    private static final Map<String, Integer> SEVERITY_ORDER = Map.of(
        "critical", 0,
        "high",     1,
        "medium",   2,
        "low",      3
    );

    public GitHubTool() {
        this.githubToken = getEnv("GITHUB_TOKEN");
        this.repoOwner   = getEnv("GITHUB_REPO_OWNER");
        this.repoName    = getEnv("GITHUB_REPO_NAME");
    }

    public List<DependabotAlert> getOpenAlerts() {
        String url = String.format(
            "https://api.github.com/repos/%s/%s/dependabot/alerts?state=open&per_page=100",
            repoOwner, repoName
        );

        Request request = new Request.Builder()
            .url(url)
            .header("Authorization", "Bearer " + githubToken)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .get()
            .build();

        try (Response response = client.newCall(request).execute()) {

            if (!response.isSuccessful()) {
                log.error("GitHub API error: {} {}", 
                    response.code(), response.message());
                return Collections.emptyList();
            }

            String body = response.body().string();
            List<DependabotAlert> alerts = mapper.readValue(
                body,
                new TypeReference<List<DependabotAlert>>() {}
            );

            // Sort by severity — critical first
            alerts.sort(Comparator.comparingInt(
                a -> SEVERITY_ORDER.getOrDefault(
                    a.getSeverity().toLowerCase(), 4)
            ));

            log.info("Found {} open Dependabot alerts", alerts.size());
            return alerts;

        } catch (Exception e) {
            log.error("Failed to fetch Dependabot alerts: {}", e.getMessage());
            return Collections.emptyList();
        }
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