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
        log.info("🤖 VulnBot Phase 1 Starting...");

        VulnBotConfig config = configLoader.load();

        if (config == null || config.getTeams().isEmpty()) {
            log.error("No config found — exiting");
            return;
        }

        String githubOwner = config.getGithubOwner();

        for (TeamConfig team : config.getTeams()) {
            log.info("Processing Team: {}", team.getTeamName());

            List<GroupedAlert> groupedAlerts =
                githubTool.getGroupedAlerts(
                    githubOwner,        // ← pass owner from config
                    team.getRepos()
                );

            // rest unchanged
        }
    }
}