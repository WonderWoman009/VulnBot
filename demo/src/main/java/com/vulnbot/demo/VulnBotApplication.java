package com.vulnbot.demo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class VulnBotApplication implements CommandLineRunner {

private static final Logger log = LoggerFactory.getLogger(VulnBotApplication.class);

    @Autowired
    private VulnBotOrchestrator orchestrator;

    public static void main(String[] args) {
        SpringApplication.run(VulnBotApplication.class, args);
    }

    @Override
    public void run(String... args) {
        orchestrator.run();
    }
}
