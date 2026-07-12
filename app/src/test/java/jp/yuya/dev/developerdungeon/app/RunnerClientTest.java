package jp.yuya.dev.developerdungeon.app;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class RunnerClientTest {
    private static final String TOKEN = "a".repeat(43);

    @Test
    void acceptsOnlyTheFixedLoopbackRunnerEndpoint() {
        assertThatCode(() -> new RunnerClient(new RunnerClientProperties("http://127.0.0.1:18081", TOKEN))).doesNotThrowAnyException();
        assertThatThrownBy(() -> new RunnerClient(new RunnerClientProperties("http://localhost:18081", TOKEN)))
                .isInstanceOf(IllegalStateException.class);
    }
}
