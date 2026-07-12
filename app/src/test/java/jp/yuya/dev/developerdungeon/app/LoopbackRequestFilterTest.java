package jp.yuya.dev.developerdungeon.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class LoopbackRequestFilterTest {
    private final LoopbackRequestFilter filter = new LoopbackRequestFilter();

    @Test void rejectsWrongHostAndCrossOriginPost() throws Exception {
        var wrongHost = new MockHttpServletRequest("GET", "/");
        var wrongHostResponse = new MockHttpServletResponse();
        filter.doFilter(wrongHost, wrongHostResponse, mock(jakarta.servlet.FilterChain.class));
        assertThat(wrongHostResponse.getStatus()).isEqualTo(400);

        var crossOrigin = new MockHttpServletRequest("POST", "/commands");
        crossOrigin.addHeader("Host", "127.0.0.1:8080"); crossOrigin.addHeader("Origin", "https://example.invalid");
        var crossOriginResponse = new MockHttpServletResponse();
        filter.doFilter(crossOrigin, crossOriginResponse, mock(jakarta.servlet.FilterChain.class));
        assertThat(crossOriginResponse.getStatus()).isEqualTo(403);
    }

    @Test void addsCspForAllowedLoopbackRequest() throws Exception {
        var request = new MockHttpServletRequest("GET", "/"); request.addHeader("Host", "127.0.0.1:8080");
        var response = new MockHttpServletResponse(); var chain = mock(jakarta.servlet.FilterChain.class);
        filter.doFilter(request, response, chain);
        verify(chain).doFilter(request, response);
        assertThat(response.getHeader("Content-Security-Policy")).contains("object-src 'none'");
    }
}
