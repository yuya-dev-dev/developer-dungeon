package jp.yuya.dev.developerdungeon.runner;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

@ConfigurationProperties(prefix = "developer-dungeon.runner")
public record RunnerProperties(String token, String imageId, String imageFingerprint, String dockerExecutable, String ledgerPath) {
    public RunnerProperties(String token, String imageId, String imageFingerprint, String dockerExecutable) { this(token, imageId, imageFingerprint, dockerExecutable, null); }
    @ConstructorBinding
    public RunnerProperties {
        dockerExecutable = dockerExecutable == null || dockerExecutable.isBlank() ? "docker" : dockerExecutable;
    }
}
