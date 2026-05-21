// orchestrator/VulnBotOrchestrator.java
package com.vulnbot.demo;

import com.vulnbot.demo.config.ConfigLoader;
import com.vulnbot.demo.config.TeamConfig;
import com.vulnbot.demo.config.VulnBotConfig;
import com.vulnbot.demo.model.GroupedAlert;
import com.vulnbot.demo.tools.AdoTool;
import com.vulnbot.demo.tools.GitHubTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class VulnBotOrchestrator {

    private static final Logger log =
        LoggerFactory.getLogger(VulnBotOrchestrator.class);

    @Autowired
    private ConfigLoader configLoader;

    @Autowired
    private GitHubTool githubTool;

    @Autowired
    private AdoTool adoTool;

    public void run() {
        log.info("═══════════════════════════════════════");
        log.info("🤖 VulnBot Phase 1 Starting...");
        log.info("═══════════════════════════════════════");
    
        VulnBotConfig config = configLoader.load();
    
        if (config == null || config.getTeams().isEmpty()) {
            log.error("No config found — exiting");
            return;
        }
    
        String githubOwner = config.getGithubOwner();
    
        int totalCreated = 0;
        int totalSkipped = 0;
        int totalFailed  = 0;
    
        for (TeamConfig team : config.getTeams()) {
            log.info("───────────────────────────────────────");
            log.info("Processing Team: {} | Repos: {}",
                team.getTeamName(), team.getRepos());
    
            List<GroupedAlert> groupedAlerts =
                githubTool.getGroupedAlerts(githubOwner, team.getRepos());
    
            if (groupedAlerts.isEmpty()) {
                log.info("✅ No open alerts for team {}", team.getTeamName());
                continue;
            }
    
            log.info("Found {} unique vulnerabilities for team {}",
                groupedAlerts.size(), team.getTeamName());
    
            int created = 0;
            int skipped = 0;
            int failed  = 0;
    
            for (GroupedAlert alert : groupedAlerts) {
                log.info("→ Processing: [{}] {} — Repos: {}",
                    alert.getSeverity().toUpperCase(),
                    alert.getPackageName(),
                    alert.getAffectedReposSummary()
                );
    
                String result = adoTool.createWorkItem(alert, team);
    
                switch (result) {
                    case "SKIPPED" -> skipped++;
                    case "FAILED"  -> failed++;
                    default        -> created++;
                }
            }
    
            log.info("Team {} — ✅ Created: {} | ⏭️ Skipped: {} | ❌ Failed: {}",
                team.getTeamName(), created, skipped, failed);
    
            totalCreated += created;
            totalSkipped += skipped;
            totalFailed  += failed;
        }
    
        log.info("═══════════════════════════════════════");
        log.info("🤖 VulnBot Complete");
        log.info("✅ Total Created : {}", totalCreated);
        log.info("⏭️  Total Skipped : {}", totalSkipped);
        log.info("❌ Total Failed  : {}", totalFailed);
        log.info("═══════════════════════════════════════");
    }
}