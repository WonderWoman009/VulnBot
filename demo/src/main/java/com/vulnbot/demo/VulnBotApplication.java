package com.vulnbot.demo;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import com.vulnbot.demo.model.DependabotAlert;
import com.vulnbot.demo.tools.AdoTool;
import com.vulnbot.demo.tools.GitHubTool;

@SpringBootApplication
public class VulnBotApplication {

private static final Logger log = LoggerFactory.getLogger(VulnBotApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(VulnBotApplication.class, args);
    }

    @Bean
    public CommandLineRunner run(
            GitHubTool githubTool, 
            AdoTool adoTool) {
        return args -> {
            log.info("═══════════════════════════════════");
            log.info("🤖 VulnBot Phase 1 Starting...");
            log.info("═══════════════════════════════════");

            List<DependabotAlert> alerts = githubTool.getOpenAlerts();

            if (alerts.isEmpty()) {
                log.info("✅ No open alerts found");
                return;
            }

            int created = 0;
            int skipped = 0;
            int failed  = 0;

            for (DependabotAlert alert : alerts) {
                log.info("Processing Alert #{} [{}] {}",
                    alert.getNumber(),
                    alert.getSeverity().toUpperCase(),
                    alert.getPackageName()
                );

                String result = adoTool.createWorkItem(alert, null);

                switch (result) {
                    case "SKIPPED" -> skipped++;
                    case "FAILED"  -> failed++;
                    default        -> created++;
                }
            }

            log.info("═══════════════════════════════════");
            log.info("🤖 VulnBot Complete");
            log.info("✅ Created : {}", created);
            log.info("⏭️  Skipped : {}", skipped);
            log.info("❌ Failed  : {}", failed);
            log.info("═══════════════════════════════════");
        };
    }

}
