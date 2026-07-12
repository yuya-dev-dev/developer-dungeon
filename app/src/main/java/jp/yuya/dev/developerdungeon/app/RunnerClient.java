package jp.yuya.dev.developerdungeon.app;

import jp.yuya.dev.developerdungeon.contract.*;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
class RunnerClient {
    private final RestClient client;
    RunnerClient(RunnerClientProperties properties) {
        if (properties.token() == null || !properties.token().matches("[A-Za-z0-9_-]{43}")) {
            throw new IllegalStateException("Runner token is not configured");
        }
        if (!"http://127.0.0.1:18081".equals(properties.baseUrl())) {
            throw new IllegalStateException("Runner URL must be the fixed loopback endpoint");
        }
        client = RestClient.builder().baseUrl(properties.baseUrl())
                .defaultHeader("X-Developer-Dungeon-Runner-Token", properties.token()).build();
    }
    WorkspaceResponse create(WorkspaceRequest request) { return client.post().uri("/internal/workspaces").body(request).retrieve().body(WorkspaceResponse.class); }
    CommandResponse execute(ExecuteRequest request) { return client.post().uri("/internal/commands").body(request).retrieve().body(CommandResponse.class); }
    void destroy(DestroyRequest request) { client.post().uri("/internal/workspaces/destroy").body(request).retrieve().toBodilessEntity(); }
}
