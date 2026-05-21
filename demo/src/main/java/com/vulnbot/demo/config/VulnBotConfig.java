// config/VulnBotConfig.java
package com.vulnbot.demo.config;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.util.List;

@Data
@AllArgsConstructor
public class VulnBotConfig {
    private String githubOwner;
    private List<TeamConfig> teams;
}