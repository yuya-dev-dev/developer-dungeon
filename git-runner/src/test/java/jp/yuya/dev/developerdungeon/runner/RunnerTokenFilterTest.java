package jp.yuya.dev.developerdungeon.runner;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RunnerTokenFilterTest {
    private static final String TOKEN = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    @Test void rejectsMissingAndDuplicateTokenBeforeChain() throws Exception {
        var filter = new RunnerTokenFilter(new RunnerProperties(TOKEN, "sha256:" + "a".repeat(64), "b".repeat(64), "C:\\docker.exe"));
        var missing = new MockHttpServletRequest("GET", "/internal/health"); var missingResponse = new MockHttpServletResponse();
        filter.doFilter(missing, missingResponse, mock(jakarta.servlet.FilterChain.class));
        assertThat(missingResponse.getStatus()).isEqualTo(401);
        var duplicate = new MockHttpServletRequest("GET", "/internal/health"); duplicate.addHeader(RunnerTokenFilter.HEADER, TOKEN); duplicate.addHeader(RunnerTokenFilter.HEADER, TOKEN);
        var duplicateResponse = new MockHttpServletResponse();
        filter.doFilter(duplicate, duplicateResponse, mock(jakarta.servlet.FilterChain.class));
        assertThat(duplicateResponse.getStatus()).isEqualTo(401);
    }
    @Test void passesExactlyOneValidToken() throws Exception {
        var filter = new RunnerTokenFilter(new RunnerProperties(TOKEN, "sha256:" + "a".repeat(64), "b".repeat(64), "C:\\docker.exe"));
        var request = new MockHttpServletRequest("GET", "/internal/health"); request.addHeader(RunnerTokenFilter.HEADER, TOKEN);
        var response = new MockHttpServletResponse(); var chain = mock(jakarta.servlet.FilterChain.class);
        filter.doFilter(request, response, chain);
        verify(chain).doFilter(request, response);
    }
}
