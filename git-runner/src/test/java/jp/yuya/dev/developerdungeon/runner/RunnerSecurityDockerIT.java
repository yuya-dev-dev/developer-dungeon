package jp.yuya.dev.developerdungeon.runner;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import jp.yuya.dev.developerdungeon.contract.DestroyRequest;
import jp.yuya.dev.developerdungeon.contract.WorkspaceRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RunnerSecurityDockerIT {
    private static final Duration DOCKER_TIMEOUT = Duration.ofSeconds(5);
    private RunnerProperties properties;
    private DockerGateway docker;

    @BeforeEach
    void requireCleanTestBoundary() {
        properties = new RunnerProperties("integration-test-token", required("DEVELOPER_DUNGEON_CHALLENGE_IMAGE_ID"),
                required("DEVELOPER_DUNGEON_CHALLENGE_IMAGE_FINGERPRINT"), required("DEVELOPER_DUNGEON_DOCKER_EXECUTABLE"));
        docker = new DockerGateway(properties);
        var existing = docker.run(List.of("ps", "-aq", "--filter", "label=io.developer-dungeon.project=developer-dungeon",
                "--filter", "label=io.developer-dungeon.owner=git-runner"), DOCKER_TIMEOUT);
        assertThat(existing.exitCode()).isZero();
        assertThat(existing.stdout()).as("Developer Dungeon must be stopped and stale containers handled manually before this test").isBlank();
    }

    @Test
    void appliesIsolationAndDeletesOnlyTheCreatedContainer() {
        MemoryContainerOwnershipLedger ledger = new MemoryContainerOwnershipLedger(Clock.systemUTC());
        RunnerWorkspaceService service = service(ledger);
        String attemptId = UUID.randomUUID().toString();
        String containerId = null;
        try {
            var workspace = service.create(new WorkspaceRequest(attemptId, UUID.randomUUID().toString(), "STAGE-GIT-01", 0));
            containerId = ledger.entries().getFirst().containerId();
            var inspect = docker.run(List.of("container", "inspect", "--format",
                    "{{.Image}}|{{.Config.User}}|{{.HostConfig.NetworkMode}}|{{.HostConfig.ReadonlyRootfs}}|{{.HostConfig.Privileged}}|{{.HostConfig.PidMode}}|{{.HostConfig.IpcMode}}|{{.HostConfig.Memory}}|{{.HostConfig.NanoCpus}}|{{.HostConfig.PidsLimit}}|{{json .HostConfig.CapDrop}}|{{json .HostConfig.SecurityOpt}}|{{json .HostConfig.Tmpfs}}|{{json .Mounts}}|{{json .HostConfig.Devices}}|{{range $key, $value := .NetworkSettings.Networks}}{{$key}},{{end}}|{{json .Config.Env}}|{{json .Config.Labels}}",
                    containerId), DOCKER_TIMEOUT);
            assertThat(inspect.exitCode()).isZero();
            String[] values = inspect.stdout().trim().split("\\|", -1);
            assertThat(values).hasSize(18);
            assertThat(values[0]).isEqualTo(properties.imageId());
            assertThat(values[1]).isEqualTo("10001:10001");
            assertThat(values[2]).isEqualTo("none");
            assertThat(values[3]).isEqualTo("true");
            assertThat(values[4]).isEqualTo("false");
            assertThat(values[5]).isNotEqualTo("host");
            assertThat(values[6]).isNotEqualTo("host");
            assertThat(values[7]).isEqualTo("268435456");
            assertThat(values[8]).isEqualTo("500000000");
            assertThat(values[9]).isEqualTo("64");
            assertThat(values[10]).containsIgnoringCase("all");
            assertThat(values[11]).contains("no-new-privileges").doesNotContain("unconfined");
            assertThat(values[12]).contains("/workspace", "/tmp", "nosuid", "nodev", "noexec", "size=64m", "size=16m");
            assertThat(values[13]).isEqualTo("[]");
            assertThat(values[14]).isEqualTo("[]");
            assertThat(values[15]).isEqualTo("none,");
            assertThat(values[16]).doesNotContain("DEVELOPER_DUNGEON_RUNNER_TOKEN", "credential", "PASSWORD", "SECRET");
            assertThat(values[17]).contains("\"io.developer-dungeon.project\":\"developer-dungeon\"",
                    "\"io.developer-dungeon.owner\":\"git-runner\"", attemptId, workspace.workspaceId(), properties.imageFingerprint());

            var daemonSecurity = docker.run(List.of("info", "--format", "{{json .SecurityOptions}}"), DOCKER_TIMEOUT);
            assertThat(daemonSecurity.exitCode()).isZero();
            assertThat(daemonSecurity.stdout()).containsIgnoringCase("seccomp").doesNotContain("unconfined");

            service.destroy(new DestroyRequest(attemptId, UUID.randomUUID().toString(), workspace.workspaceId(), 0, "integration-test"));
            assertContainerDoesNotExist(containerId);
            containerId = null;
        } finally {
            removeRecordedContainer(containerId);
        }
    }

    @Test
    void startupSweepRecoversRecordedOrphan() {
        MemoryContainerOwnershipLedger ledger = new MemoryContainerOwnershipLedger(Clock.systemUTC());
        RunnerWorkspaceService first = service(ledger);
        String containerId = null;
        try {
            first.create(new WorkspaceRequest(UUID.randomUUID().toString(), UUID.randomUUID().toString(), "STAGE-GIT-01", 0));
            containerId = ledger.entries().getFirst().containerId();

            RunnerWorkspaceService restarted = service(ledger);
            restarted.cleanupOrphansOnStartup();

            assertThat(restarted.isReady()).isTrue();
            assertThat(ledger.entries()).isEmpty();
            assertContainerDoesNotExist(containerId);
            containerId = null;
        } finally {
            removeRecordedContainer(containerId);
        }
    }

    private RunnerWorkspaceService service(ContainerOwnershipLedger ledger) {
        return new RunnerWorkspaceService(docker, properties, new RunnerCommandValidator(), Clock.systemUTC(), ledger);
    }

    private void assertContainerDoesNotExist(String containerId) {
        var inspect = docker.run(List.of("container", "inspect", containerId), DOCKER_TIMEOUT);
        assertThat(inspect.exitCode()).isNotZero();
    }

    private void removeRecordedContainer(String containerId) {
        if (containerId != null && containerId.matches("[0-9a-f]{12,64}")) {
            docker.run(List.of("rm", "-f", containerId), DOCKER_TIMEOUT);
        }
    }

    private String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException(name + " is required for RunnerSecurityDockerIT");
        return value;
    }
}
