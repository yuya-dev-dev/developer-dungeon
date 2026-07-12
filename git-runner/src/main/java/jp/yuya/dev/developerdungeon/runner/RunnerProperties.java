package jp.yuya.dev.developerdungeon.runner;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "developer-dungeon.runner")
public record RunnerProperties(String token, String imageId, String imageFingerprint, String dockerExecutable) {
    public RunnerProperties {
        dockerExecutable = dockerExecutable == null || dockerExecutable.isBlank() ? "docker" : dockerExecutable;
    }
}
