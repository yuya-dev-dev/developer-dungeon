package jp.yuya.dev.developerdungeon.app;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "developer-dungeon.runner-client")
public record RunnerClientProperties(String baseUrl, String token) { }
