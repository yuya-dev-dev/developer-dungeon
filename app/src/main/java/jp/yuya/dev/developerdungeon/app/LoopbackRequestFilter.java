package jp.yuya.dev.developerdungeon.app;

import java.io.IOException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
class LoopbackRequestFilter extends OncePerRequestFilter {
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        String host = request.getHeader("Host");
        if (host == null || !(host.equals("127.0.0.1:8080") || host.equals("localhost:8080"))) { response.sendError(400); return; }
        String origin = request.getHeader("Origin");
        if (!request.getRequestURI().startsWith("/internal/") && !HttpMethod.GET.matches(request.getMethod()) && origin != null
                && !(origin.equals("http://127.0.0.1:8080") || origin.equals("http://localhost:8080"))) { response.sendError(403); return; }
        response.setHeader("Content-Security-Policy", "default-src 'self'; script-src 'self'; style-src 'self'; object-src 'none'; base-uri 'none'; frame-ancestors 'none'; form-action 'self'");
        chain.doFilter(request, response);
    }
}
