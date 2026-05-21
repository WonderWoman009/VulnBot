// config/ConfigLoader.java
package com.vulnbot.demo.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

@Component
public class ConfigLoader {

    private static final Logger log = 
        LoggerFactory.getLogger(ConfigLoader.class);
    
    private final ObjectMapper mapper = new ObjectMapper();

    public VulnBotConfig load() {
        try {
            File configFile = new File("config.json");

            if (!configFile.exists()) {
                log.error("config.json not found at: {}",
                    configFile.getAbsolutePath());
                return null;
            }

            JsonNode root = mapper.readTree(configFile);

            // Read global owner
            String githubOwner = root.path("githubOwner").asText();

            List<TeamConfig> teams = new ArrayList<>();
            for (JsonNode teamNode : root.path("teams")) {
                TeamConfig team = new TeamConfig();
                team.setTeamId(teamNode.path("teamId").asText());
                team.setTeamName(teamNode.path("teamName").asText());
                team.setAdoProject(teamNode.path("adoProject").asText());
                team.setAdoAreaPath(teamNode.path("adoAreaPath").asText());
                team.setAdoFeatureId(teamNode.path("adoFeatureId").asText());

                List<String> repos = new ArrayList<>();
                for (JsonNode repoNode : teamNode.path("repos")) {
                    repos.add(repoNode.asText());
                }
                team.setRepos(repos);
                teams.add(team);
            }

            log.info("Loaded {} teams — GitHub owner: {}",
                teams.size(), githubOwner);

            return new VulnBotConfig(githubOwner, teams);

        } catch (Exception e) {
            log.error("Failed to load config.json: {}", e.getMessage());
            return null;
        }
    }
}