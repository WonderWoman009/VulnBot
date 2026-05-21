// tools/GitHubTool.java
package com.vulnbot.demo.tools;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vulnbot.demo.model.DependabotAlert;
import com.vulnbot.demo.model.GroupedAlert;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class GitHubTool {

    private static final Logger log = LoggerFactory.getLogger(GitHubTool.class);

    private final OkHttpClient client = new OkHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${github.token}")
    private String githubToken;

    private static final Map<String, Integer> SEVERITY_ORDER = Map.of(
        "critical", 0,
        "high",     1,
        "medium",   2,
        "low",      3
    );

    /**
     * Fetch alerts from a single repo
     */
    public List<DependabotAlert> getOpenAlerts(String owner, String repoName) {
        String url = String.format(
            "https://api.github.com/repos/%s/%s/dependabot/alerts"
            + "?state=open&per_page=100",
            owner, repoName    // ← owner passed in
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
                log.error("GitHub API error for repo {}: {} {}",
                    repoName,
                    response.code(),
                    response.message());
                return Collections.emptyList();
            }

            List<DependabotAlert> alerts = mapper.readValue(
                response.body().string(),
                new TypeReference<List<DependabotAlert>>() {}
            );

            log.info("Repo {} — {} open alerts",
                repoName, alerts.size());
            return alerts;

        } catch (Exception e) {
            log.error("Failed to fetch alerts for repo {}: {}",
                repoName, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Fetch and group alerts across multiple repos for a team
     * Same package + severity across repos = one grouped alert
     */
    public List<GroupedAlert> getGroupedAlerts(String owner, List<String> repos) {

        // Map of groupingKey → GroupedAlert
        Map<String, GroupedAlert> groupedMap = new LinkedHashMap<>();

        for (String repoName : repos) {
            List<DependabotAlert> alerts = getOpenAlerts(owner, repoName);

            for (DependabotAlert alert : alerts) {
                String key = alert.getPackageName() + "_" 
                           + alert.getSeverity().toLowerCase();

                if (groupedMap.containsKey(key)) {
                    // Already exists — just add this repo to affected list
                    GroupedAlert existing = groupedMap.get(key);
                    existing.getAffectedRepos().add(repoName);
                    existing.getAlertUrls().add(alert.getHtmlUrl());
                    existing.getAlertNumbers().add(alert.getNumber());
                    log.info("Grouped alert for {} also affects repo {}",
                        alert.getPackageName(), repoName);
                } else {
                    // New grouped alert
                    GroupedAlert grouped = GroupedAlert.builder()
                        .packageName(alert.getPackageName())
                        .severity(alert.getSeverity())
                        .cveId(alert.getSecurityAdvisory().getCveId())
                        .summary(alert.getSecurityAdvisory().getSummary())
                        .safeVersion(alert.getSafeVersion())
                        .vulnerableRange(
                            alert.getSecurityVulnerability()
                                .getVulnerableVersionRange())
                        .affectedRepos(
                            new ArrayList<>(List.of(repoName)))
                        .alertUrls(
                            new ArrayList<>(List.of(alert.getHtmlUrl())))
                        .alertNumbers(
                            new ArrayList<>(List.of(alert.getNumber())))
                        .build();
                    groupedMap.put(key, grouped);
                }
            }
        }

        // Sort by severity
        List<GroupedAlert> result = new ArrayList<>(groupedMap.values());
        result.sort(Comparator.comparingInt(
            a -> SEVERITY_ORDER.getOrDefault(
                a.getSeverity().toLowerCase(), 4)
        ));

        log.info("Total grouped alerts: {} (from {} repos)",
            result.size(), repos.size());
        return result;
    }
}