package com.vulnbot.demo.config;

import lombok.Data;
import java.util.List;

@Data
public class TeamConfig {
    private String teamId;
    private String teamName;
    private String adoAreaPath;
    private String adoProject; 
    private String adoFeatureId;
    private List<String> repos;
}