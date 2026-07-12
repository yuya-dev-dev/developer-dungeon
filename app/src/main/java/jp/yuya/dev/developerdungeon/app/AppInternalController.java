package jp.yuya.dev.developerdungeon.app;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal")
class AppInternalController {
    private final RunnerClientProperties properties;
    private final ConfigurableApplicationContext context;
    AppInternalController(RunnerClientProperties properties, ConfigurableApplicationContext context) { this.properties = properties; this.context = context; }
    @GetMapping("/health") ResponseEntity<Void> health(HttpServletRequest request, @RequestHeader(name = "X-Developer-Dungeon-Runner-Token", required = false) String token) { authorize(request, token); return ResponseEntity.noContent().build(); }
    @PostMapping("/shutdown") ResponseEntity<Void> shutdown(HttpServletRequest request, @RequestHeader(name = "X-Developer-Dungeon-Runner-Token", required = false) String token) {
        authorize(request, token); Thread.ofVirtual().start(() -> SpringApplication.exit(context)); return ResponseEntity.noContent().build();
    }
    private void authorize(HttpServletRequest request, String value) {
        String expected = properties.token();
        if (java.util.Collections.list(request.getHeaders("X-Developer-Dungeon-Runner-Token")).size() != 1 || value == null || expected == null || !MessageDigest.isEqual(value.getBytes(StandardCharsets.US_ASCII), expected.getBytes(StandardCharsets.US_ASCII))) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED);
        }
    }
}
