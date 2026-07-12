package jp.yuya.dev.developerdungeon.app;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;

class OutputSanitizerTest {
    @Test void replacesAnsiEscapeAndControlCharacters() {
        String safe = new OutputSanitizer().sanitize("<script>x</script>\u001b[31m\u0001\u0085");
        assertThat(safe).contains("<script>x</script>").doesNotContain("\u001b").doesNotContain("\u0085").contains("�");
    }
}
