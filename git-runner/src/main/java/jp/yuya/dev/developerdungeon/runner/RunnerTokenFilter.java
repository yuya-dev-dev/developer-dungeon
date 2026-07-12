package jp.yuya.dev.developerdungeon.runner;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
class RunnerTokenFilter extends OncePerRequestFilter {
    static final String HEADER = "X-Developer-Dungeon-Runner-Token";
    private final RunnerProperties properties;

    RunnerTokenFilter(RunnerProperties properties) { this.properties = properties; }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!request.getRequestURI().startsWith("/internal/")) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        var values = java.util.Collections.list(request.getHeaders(HEADER));
        if (values.size() != 1 || !matches(values.getFirst(), properties.token())) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean matches(String actual, String expected) {
        if (actual == null || expected == null || !expected.matches("[A-Za-z0-9_-]{43}")) return false;
        return MessageDigest.isEqual(actual.getBytes(StandardCharsets.US_ASCII), expected.getBytes(StandardCharsets.US_ASCII));
    }
}
