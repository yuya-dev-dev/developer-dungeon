package jp.yuya.dev.developerdungeon.runner;

import jp.yuya.dev.developerdungeon.contract.CommandResponse;
import jp.yuya.dev.developerdungeon.contract.DestroyRequest;
import jp.yuya.dev.developerdungeon.contract.ExecuteRequest;
import jp.yuya.dev.developerdungeon.contract.FileContentResponse;
import jp.yuya.dev.developerdungeon.contract.ReadFileRequest;
import jp.yuya.dev.developerdungeon.contract.RepositorySnapshot;
import jp.yuya.dev.developerdungeon.contract.WorkspaceRequest;
import jp.yuya.dev.developerdungeon.contract.WorkspaceResponse;
import jp.yuya.dev.developerdungeon.contract.WriteFileRequest;
import jp.yuya.dev.developerdungeon.contract.WriteFileResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.boot.SpringApplication;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal")
class RunnerController {
    private final RunnerWorkspaceService service;
    private final ConfigurableApplicationContext context;
    RunnerController(RunnerWorkspaceService service, ConfigurableApplicationContext context) { this.service = service; this.context = context; }

    @GetMapping("/health") ResponseEntity<Void> health() {
        return service.isReady() ? ResponseEntity.noContent().build() : ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
    }
    @PostMapping("/shutdown") ResponseEntity<Void> shutdown() {
        try { service.beginShutdown(); }
        catch (RuntimeException exception) { throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "challenge cleanup is incomplete"); }
        Thread.ofVirtual().start(() -> SpringApplication.exit(context));
        return ResponseEntity.noContent().build();
    }
    @PostMapping("/workspaces") WorkspaceResponse create(@RequestBody WorkspaceRequest request) { return service.create(request); }
    @PostMapping("/commands") CommandResponse execute(@RequestBody ExecuteRequest request) { return service.execute(request); }
    @PostMapping("/files/read") FileContentResponse readFile(@RequestBody ReadFileRequest request) { return service.readFile(request); }
    @PostMapping("/files/write") WriteFileResponse writeFile(@RequestBody WriteFileRequest request) { return service.writeFile(request); }
    @PostMapping("/workspaces/destroy") ResponseEntity<Void> destroy(@RequestBody DestroyRequest request) { service.destroy(request); return ResponseEntity.noContent().build(); }
    @GetMapping("/workspaces/{workspaceId}/snapshot") RepositorySnapshot snapshot(
            @PathVariable String workspaceId, @RequestParam String attemptId, @RequestParam long generation) {
        return service.snapshotFor(workspaceId, attemptId, generation);
    }
}
