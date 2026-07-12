package jp.yuya.dev.developerdungeon.app;

import java.util.UUID;
import java.util.HashSet;
import jp.yuya.dev.developerdungeon.contract.*;
import org.springframework.stereotype.Service;

@Service
class StageOneService {
    private final RunnerClient runner;
    private final GitCommandParser parser;
    private final StageOneGrader grader;
    private final OutputSanitizer sanitizer;
    private Attempt attempt;

    StageOneService(RunnerClient runner, GitCommandParser parser, StageOneGrader grader, OutputSanitizer sanitizer) {
        this.runner = runner; this.parser = parser; this.grader = grader; this.sanitizer = sanitizer;
    }

    synchronized StageView open() {
        if (attempt == null) attempt = create(UUID.randomUUID().toString(), 0, 0, 0, 0, 0);
        return attempt.view();
    }

    synchronized StageView execute(String raw, String requestId) {
        if (attempt == null) attempt = create(UUID.randomUUID().toString(), 0, 0, 0, 0, 0);
        if (requestId == null || !requestId.matches("[0-9a-f-]{36}")) throw new IllegalArgumentException("request ID is invalid");
        StageView recorded = attempt.completedRequests.get(requestId);
        if (recorded != null) return recorded;
        if (attempt.closed) {
            attempt.lastOutput = "この課題はクリア済みです。最初からやり直す場合はリセットしてください。";
            return attempt.view();
        }
        try {
            GitCommand command = parser.parse(raw);
            GitCommand normalized = normalize(command, attempt);
            attempt.commandSequence++;
            CommandResponse response = runner.execute(new ExecuteRequest(attempt.attemptId, requestId,
                    attempt.workspaceId, attempt.generation, normalized));
            attempt.lastOutput = sanitizer.sanitize(response.stdout() + (response.stderr().isBlank() ? "" : "\n" + response.stderr()) + (response.outputTruncated() ? "\n[output truncated]" : ""));
            attempt.lastExitCode = response.exitCode();
            attempt.snapshot = response.snapshot();
            if (normalized.kind() == CommandKind.LOG_ONELINE) {
                response.stdout().lines().map(String::strip).filter(line -> line.matches("[0-9a-f]{12} .*"))
                        .map(line -> line.substring(0, 12)).forEach(attempt.displayedShortIds::add);
            }
            attempt.grade = grader.grade(response.snapshot(), attempt.badCommitId, attempt.safeTreeId, attempt.highestHint, attempt.playerResets);
            if (attempt.grade.cleared()) {
                destroy(attempt, "stage-cleared");
                attempt.closed = true;
            }
        } catch (IllegalArgumentException exception) {
            attempt.lastOutput = exception.getMessage(); attempt.lastExitCode = null;
        } catch (RuntimeException exception) {
            Attempt previous = attempt;
            try {
                attempt = recover(previous);
                attempt.lastOutput = "Runnerへ接続できません。安全のため操作は実行されませんでした。新しい作業環境を用意しました。";
                attempt.lastExitCode = null;
            } catch (RuntimeException recoveryFailure) {
                previous.lastOutput = "Runnerへ接続できません。安全のため操作は実行されませんでした。再接続後にもう一度試してください。";
                previous.lastExitCode = null;
                previous.grade = new StageOneGrader.Grade(false, 0, "未復旧");
                previous.closed = false;
                attempt = previous;
            }
        }
        StageView result = attempt.view();
        attempt.completedRequests.put(requestId, result);
        return result;
    }

    synchronized StageView hint() {
        if (attempt == null) attempt = create(UUID.randomUUID().toString(), 0, 0, 0, 0, 0);
        attempt.highestHint = Math.min(4, attempt.highestHint + 1);
        return attempt.view();
    }

    synchronized StageView reset() {
        boolean systemRecovery = false;
        if (attempt != null) {
            if (!attempt.closed) {
                try { destroy(attempt, "player-reset"); }
                catch (RuntimeException exception) {
                    attempt.lastOutput = "旧環境の安全な削除を確認できないため、新しい作業環境は作成しません。再接続後にもう一度リセットしてください。";
                    attempt.lastExitCode = null;
                    return attempt.view();
                }
            }
            attempt = create(attempt.attemptId, attempt.highestHint, attempt.playerResets + 1, attempt.systemRecoveryCount + (systemRecovery ? 1 : 0), attempt.generation + 1, attempt.commandSequence);
        } else attempt = create(UUID.randomUUID().toString(), 0, 1, 0, 0, 0);
        return attempt.view();
    }

    private Attempt create(String attemptId, int hint, int resets, int systemRecoveryCount, long generation, long commandSequence) {
        WorkspaceResponse workspace = runner.create(new WorkspaceRequest(attemptId, UUID.randomUUID().toString(), "STAGE-GIT-01", generation));
        RepositorySnapshot initial = workspace.snapshot();
        if (initial.headParents().isEmpty()) throw new IllegalStateException("invalid stage fixture");
        return new Attempt(attemptId, workspace.workspaceId(), workspace.generation(), initial, initial.headObjectId(),
                initial.firstParentTreeId(), hint, resets, systemRecoveryCount, commandSequence);
    }
    private Attempt recover(Attempt previous) {
        destroy(previous, "system-recovery");
        return create(previous.attemptId, previous.highestHint, previous.playerResets, previous.systemRecoveryCount + 1, previous.generation + 1, previous.commandSequence);
    }
    private void destroy(Attempt attempt, String reason) {
        if (attempt.cleanupRequestId == null) attempt.cleanupRequestId = UUID.randomUUID().toString();
        runner.destroy(new DestroyRequest(attempt.attemptId, attempt.cleanupRequestId, attempt.workspaceId, attempt.generation, reason));
        attempt.cleanupRequestId = null;
    }
    private GitCommand normalize(GitCommand command, Attempt attempt) {
        if (command.kind() != CommandKind.SHOW && command.kind() != CommandKind.REVERT_NO_EDIT) return command;
        String input = command.objectIds().getFirst();
        var candidates = attempt.snapshot.ancestorObjectIds().stream().filter(id -> id.startsWith(input)).toList();
        if (candidates.size() != 1 || (input.length() == 12 && !attempt.displayedShortIds.contains(input))) {
            throw new IllegalArgumentException("表示済みの一意なcommit IDだけを指定してください。");
        }
        return new GitCommand(command.kind(), java.util.List.of(candidates.getFirst()));
    }

    private static final class Attempt {
        final String attemptId; final String workspaceId; final long generation; final String badCommitId; final String safeTreeId;
        RepositorySnapshot snapshot; int highestHint; final int playerResets; final int systemRecoveryCount; long commandSequence; boolean closed; Integer lastExitCode; String cleanupRequestId; final HashSet<String> displayedShortIds = new HashSet<>(); final java.util.HashMap<String, StageView> completedRequests = new java.util.HashMap<>(); String lastOutput = "まずは状態を調べてみましょう。";
        StageOneGrader.Grade grade = new StageOneGrader.Grade(false, 0, "未復旧");
        Attempt(String attemptId, String workspaceId, long generation, RepositorySnapshot snapshot, String badCommitId, String safeTreeId, int highestHint, int playerResets, int systemRecoveryCount, long commandSequence) {
            this.attemptId=attemptId; this.workspaceId=workspaceId; this.generation=generation; this.snapshot=snapshot; this.badCommitId=badCommitId; this.safeTreeId=safeTreeId; this.highestHint=highestHint; this.playerResets=playerResets; this.systemRecoveryCount=systemRecoveryCount; this.commandSequence=commandSequence;
        }
        StageView view() { return new StageView(UUID.randomUUID().toString(), lastOutput, lastExitCode, snapshot, highestHint, playerResets, systemRecoveryCount, commandSequence, grade.cleared(), grade.stars(), grade.message()); }
    }
    record StageView(String requestId, String output, Integer exitCode, RepositorySnapshot snapshot, int hintLevel, int resetCount, int systemRecoveryCount, long commandSequence, boolean cleared, int stars, String gradeMessage) { }
}
