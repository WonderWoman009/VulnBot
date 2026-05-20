package com.vulnbot.demo;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import com.vulnbot.demo.model.DependabotAlert;
import com.vulnbot.demo.tools.AdoTool;
import com.vulnbot.demo.tools.GitHubTool;

@SpringBootApplication
public class VulnBotApplication {

private static final Logger log = LoggerFactory.getLogger(VulnBotApplication.class);

    public static void main(String[] args) {
        log.info("═══════════════════════════════════");
        log.info("🤖 VulnBot Phase 1 Starting...");
        log.info("═══════════════════════════════════");

        GitHubTool githubTool = new GitHubTool();
        AdoTool    adoTool    = new AdoTool();

        // Step 1: Fetch open Dependabot alerts
        List<DependabotAlert> alerts = githubTool.getOpenAlerts();

        if (alerts.isEmpty()) {
            log.info("✅ No open Dependabot alerts found. Nothing to process.");
            return;
        }

        log.info("Processing {} alerts...", alerts.size());

        // Step 2: Process each alert
        int created = 0;
        int skipped = 0;
        int failed  = 0;

        for (DependabotAlert alert : alerts) {
            log.info("─────────────────────────────────");
            log.info("Alert #{} | [{}] | {}",
                alert.getNumber(),
                alert.getSeverity().toUpperCase(),
                alert.getPackageName()
            );

            // Phase 1: No AI yet — pass null for fix suggestion
            // Phase 2 will replace null with LLM generated suggestion
            String result = adoTool.createWorkItem(alert, null);

            switch (result) {
                case "SKIPPED" -> skipped++;
                case "FAILED"  -> failed++;
                default        -> created++;
            }
        }

        // Summary
        log.info("═══════════════════════════════════");
        log.info("🤖 VulnBot Phase 1 Complete");
        log.info("✅ Created : {}", created);
        log.info("⏭️  Skipped : {}", skipped);
        log.info("❌ Failed  : {}", failed);
        log.info("═══════════════════════════════════");
    }

}
