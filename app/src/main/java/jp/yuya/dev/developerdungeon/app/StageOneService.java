package jp.yuya.dev.developerdungeon.app;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.HashSet;
import jp.yuya.dev.developerdungeon.contract.*;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

@Service
class StageOneService {
    private final RunnerClient runner;
    private final GitCommandParser parser;
    private final StageOneGrader grader;
    private final OutputSanitizer sanitizer;
    private final StagePersistence persistence;
    private final Clock clock;
    private Attempt attempt;

    @Autowired
    StageOneService(RunnerClient runner, GitCommandParser parser, StageOneGrader grader, OutputSanitizer sanitizer, StagePersistence persistence, Clock clock) {
        this.runner = runner; this.parser = parser; this.grader = grader; this.sanitizer = sanitizer; this.persistence = persistence; this.clock = clock;
    }
    StageOneService(RunnerClient runner, GitCommandParser parser, StageOneGrader grader, OutputSanitizer sanitizer) {
        this(runner, parser, grader, sanitizer, new MemoryStagePersistence(), Clock.systemUTC());
    }

    synchronized StageView open() {
        if (attempt == null) attempt = persistence.findOpen("STAGE-GIT-01").map(this::recoverSaved).orElseGet(this::newAttempt);
        return attempt.view();
    }

    StageProgress progress() {
        return new StageProgress("STAGE-GIT-01", "公開済み変更を取り消す", "公開済みの誤変更を、履歴を壊さずに戻す。", persistence.highestStars("STAGE-GIT-01"));
    }

    synchronized StageView execute(String raw, String requestId) {
        if (attempt == null) attempt = persistence.findOpen("STAGE-GIT-01").map(this::recoverSaved).orElseGet(this::newAttempt);
        if (requestId == null || !requestId.matches("[0-9a-f-]{36}")) throw new IllegalArgumentException("request ID is invalid");
        UUID persistentRequestId = UUID.fromString(requestId);
        StageView recorded = attempt.completedRequests.get(requestId);
        if (recorded != null) return recorded;
        if (attempt.closed) {
            attempt.lastOutput = "この課題はクリア済みです。最初からやり直す場合はリセットしてください。";
            return attempt.view();
        }
        GitCommand normalized;
        long sequence = attempt.commandSequence + 1;
        try {
            normalized = normalize(parser.parse(raw), attempt);
        } catch (IllegalArgumentException exception) {
            StagePersistence.SavedAttempt saved = persistence.recordRejected(UUID.fromString(attempt.attemptId), attempt.version,
                    persistentRequestId, sequence, attempt.generation, rejectionReason(exception), clock.instant());
            attempt.apply(saved);
            attempt.lastOutput = exception.getMessage(); attempt.lastExitCode = null;
            StageView result = attempt.view(); attempt.completedRequests.put(requestId, result); return result;
        }
        String canonical = normalized.kind().name() + (normalized.objectIds().isEmpty() ? "" : " " + String.join(" ", normalized.objectIds()));
        boolean started = persistence.beginCommand(UUID.fromString(attempt.attemptId), attempt.version, persistentRequestId,
                sequence, attempt.generation, canonical, normalized.kind().name(), clock.instant());
        if (!started) {
            attempt.lastOutput = "この操作は処理済みです。Git操作は再実行されませんでした。";
            attempt.lastExitCode = null;
            return attempt.view();
        }
        attempt.version++; attempt.commandSequence = sequence;
        long startedAt = System.nanoTime();
        boolean commandPending = true;
        try {
            CommandResponse response = runner.execute(new ExecuteRequest(attempt.attemptId, requestId,
                    attempt.workspaceId, attempt.generation, normalized));
            StageOneGrader.Grade grade = grader.grade(response.snapshot(), attempt.badCommitId, attempt.safeTreeId, attempt.highestHint, attempt.playerResets);
            UUID cleanupId = grade.cleared() ? UUID.randomUUID() : null;
            StagePersistence.SavedAttempt saved = persistence.finishCommand(UUID.fromString(attempt.attemptId), attempt.version,
                    persistentRequestId, response.exitCode() == 0 ? "SUCCEEDED" : "GIT_ERROR", response.exitCode(),
                    Math.max(0, (System.nanoTime() - startedAt) / 1_000_000), cleanupId, grade.cleared() ? grade.stars() : null);
            attempt.apply(saved);
            commandPending = false;
            attempt.lastOutput = sanitizer.sanitize(response.stdout() + (response.stderr().isBlank() ? "" : "\n" + response.stderr()) + (response.outputTruncated() ? "\n[output truncated]" : ""));
            attempt.lastExitCode = response.exitCode();
            attempt.snapshot = response.snapshot();
            if (normalized.kind() == CommandKind.LOG_ONELINE) {
                response.stdout().lines().map(String::strip).filter(line -> line.matches("[0-9a-f]{12} .*"))
                        .map(line -> line.substring(0, 12)).forEach(attempt.displayedShortIds::add);
            }
            attempt.grade = grade;
            if (attempt.grade.cleared()) {
                attempt.cleanupRequestId = cleanupId.toString();
                try {
                    destroy(attempt, "stage-cleared");
                    saved = persistence.completeClear(UUID.fromString(attempt.attemptId), attempt.version, clock.instant());
                    attempt.apply(saved); attempt.closed = true;
                } catch (RuntimeException cleanupFailure) {
                    saved = persistence.markCleanupPending(UUID.fromString(attempt.attemptId), attempt.version);
                    attempt.apply(saved); attempt.grade = new StageOneGrader.Grade(false, 0, "未復旧");
                    attempt.lastOutput = "クリア状態は確認できましたが、旧環境の削除を確認できません。再接続後にもう一度試してください。";
                    attempt.lastExitCode = null;
                }
            }
        } catch (RuntimeException exception) {
            Attempt previous = attempt;
            try {
                if (commandPending) {
                    StagePersistence.SavedAttempt saved = persistence.finishCommand(UUID.fromString(previous.attemptId), previous.version,
                            persistentRequestId, "RUNNER_ERROR", null, Math.max(0, (System.nanoTime() - startedAt) / 1_000_000), null, null);
                    previous.apply(saved);
                }
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
        if (attempt == null) attempt = persistence.findOpen("STAGE-GIT-01").map(this::recoverSaved).orElseGet(this::newAttempt);
        attempt.highestHint = Math.min(4, attempt.highestHint + 1);
        attempt.apply(persistence.increaseHint(UUID.fromString(attempt.attemptId), attempt.version, attempt.highestHint));
        return attempt.view();
    }

    synchronized StageView reset() {
        if (attempt != null) {
            if (attempt.closed) { attempt = newAttempt(); return attempt.view(); }
            if (!attempt.closed) {
                UUID cleanupId = attempt.cleanupRequestId == null ? UUID.randomUUID() : UUID.fromString(attempt.cleanupRequestId);
                UUID createId = UUID.randomUUID();
                attempt.apply(persistence.beginReset(UUID.fromString(attempt.attemptId), attempt.version, cleanupId, createId));
                attempt.cleanupRequestId = cleanupId.toString();
                try { destroy(attempt, "player-reset"); }
                catch (RuntimeException exception) {
                    attempt.apply(persistence.markCleanupPending(UUID.fromString(attempt.attemptId), attempt.version));
                    attempt.lastOutput = "旧環境の安全な削除を確認できないため、新しい作業環境は作成しません。再接続後にもう一度リセットしてください。";
                    attempt.lastExitCode = null;
                    return attempt.view();
                }
                StagePersistence.SavedAttempt saved = persistence.completeReset(UUID.fromString(attempt.attemptId), attempt.version, false, UUID.randomUUID(), clock.instant());
                attempt = createWorkspace(saved);
            }
        } else attempt = newAttempt();
        return attempt.view();
    }

    private Attempt newAttempt() {
        UUID attemptId = UUID.randomUUID(); UUID createId = UUID.randomUUID();
        return createWorkspace(persistence.createStarting(attemptId, "STAGE-GIT-01", createId, clock.instant()));
    }
    private Attempt createWorkspace(StagePersistence.SavedAttempt saved) {
        WorkspaceResponse workspace = runner.create(new WorkspaceRequest(saved.id().toString(), saved.createRequestId().toString(), "STAGE-GIT-01", saved.generation()));
        UUID cleanupId = saved.cleanupRequestId();
        try {
            saved = persistence.workspaceCreated(saved.id(), saved.version(), UUID.fromString(workspace.workspaceId()));
            RepositorySnapshot initial = workspace.snapshot();
            if (initial.headParents().isEmpty()) throw new IllegalStateException("invalid stage fixture");
            saved = persistence.activate(saved.id(), saved.version(), UUID.fromString(workspace.workspaceId()));
            return new Attempt(saved.id().toString(), workspace.workspaceId(), workspace.generation(), initial, initial.headObjectId(),
                    initial.firstParentTreeId(), saved.highestHint(), saved.playerResets(), saved.systemRecoveries(), saved.lastSequence(), saved.version());
        } catch (RuntimeException exception) {
            StagePersistence.SavedAttempt pending = null;
            try { pending = persistence.beginCreateCleanup(saved.id(), saved.version(), UUID.fromString(workspace.workspaceId())); }
            catch (RuntimeException stateFailure) { exception.addSuppressed(stateFailure); }
            try { runner.destroy(new DestroyRequest(saved.id().toString(), cleanupId.toString(), workspace.workspaceId(), workspace.generation(), "persistence-create-recovery")); }
            catch (RuntimeException cleanupFailure) { exception.addSuppressed(cleanupFailure); }
            if (pending != null) {
                try { persistence.restartStartingAfterCleanup(pending.id(), pending.version()); }
                catch (RuntimeException stateFailure) { exception.addSuppressed(stateFailure); }
            }
            throw exception;
        }
    }
    private Attempt recover(Attempt previous) {
        UUID cleanupId = previous.cleanupRequestId == null ? UUID.randomUUID() : UUID.fromString(previous.cleanupRequestId);
        UUID createId = UUID.randomUUID();
        StagePersistence.SavedAttempt saved = persistence.prepareSystemRecovery(UUID.fromString(previous.attemptId), previous.version, cleanupId, createId);
        previous.apply(saved); previous.cleanupRequestId = cleanupId.toString();
        destroy(previous, "system-recovery");
        saved = persistence.completeReset(UUID.fromString(previous.attemptId), previous.version, true, UUID.randomUUID(), clock.instant());
        return createWorkspace(saved);
    }
    private void destroy(Attempt attempt, String reason) {
        if (attempt.cleanupRequestId == null) attempt.cleanupRequestId = UUID.randomUUID().toString();
        runner.destroy(new DestroyRequest(attempt.attemptId, attempt.cleanupRequestId, attempt.workspaceId, attempt.generation, reason));
        attempt.cleanupRequestId = null;
    }
    private Attempt recoverSaved(StagePersistence.SavedAttempt saved) {
        if (saved.status().equals("STARTING")) return createWorkspace(saved);
        if ((saved.status().equals("CLEARING") || saved.status().equals("CLEANUP_PENDING")) && saved.pendingStars() != null) {
            runner.destroy(new DestroyRequest(saved.id().toString(), saved.cleanupRequestId().toString(), saved.workspaceId().toString(), saved.generation(), "startup-clear-recovery"));
            persistence.completeClear(saved.id(), saved.version(), clock.instant());
            return newAttempt();
        }
        UUID cleanupId = saved.cleanupRequestId() == null ? UUID.randomUUID() : saved.cleanupRequestId();
        UUID createId = saved.createRequestId() == null ? UUID.randomUUID() : saved.createRequestId();
        if (saved.status().equals("ACTIVE") || saved.status().equals("EXECUTING")) saved = persistence.prepareSystemRecovery(saved.id(), saved.version(), cleanupId, createId);
        if (!saved.status().equals("RESETTING") && !saved.status().equals("CLEANUP_PENDING")) throw new IllegalStateException("attempt recovery state is invalid");
        runner.destroy(new DestroyRequest(saved.id().toString(), cleanupId.toString(), saved.workspaceId().toString(), saved.generation(), "startup-system-recovery"));
        saved = persistence.completeReset(saved.id(), saved.version(), true, UUID.randomUUID(), clock.instant());
        return createWorkspace(saved);
    }
    private String rejectionReason(IllegalArgumentException exception) {
        String message = exception.getMessage();
        if (message != null && message.contains("commit ID")) return "OBJECT_NOT_ALLOWED";
        if (message != null && message.contains("対応していない")) return "UNKNOWN_COMMAND";
        if (message != null && message.contains("形式")) return "INVALID_SYNTAX";
        return "INVALID_ARGUMENT";
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
        RepositorySnapshot snapshot; int highestHint; int playerResets; int systemRecoveryCount; long commandSequence; long version; boolean closed; Integer lastExitCode; String cleanupRequestId; final HashSet<String> displayedShortIds = new HashSet<>(); final java.util.HashMap<String, StageView> completedRequests = new java.util.HashMap<>(); String lastOutput = "まずは状態を調べてみましょう。";
        StageOneGrader.Grade grade = new StageOneGrader.Grade(false, 0, "未復旧");
        Attempt(String attemptId, String workspaceId, long generation, RepositorySnapshot snapshot, String badCommitId, String safeTreeId, int highestHint, int playerResets, int systemRecoveryCount, long commandSequence, long version) {
            this.attemptId=attemptId; this.workspaceId=workspaceId; this.generation=generation; this.snapshot=snapshot; this.badCommitId=badCommitId; this.safeTreeId=safeTreeId; this.highestHint=highestHint; this.playerResets=playerResets; this.systemRecoveryCount=systemRecoveryCount; this.commandSequence=commandSequence; this.version=version;
        }
        void apply(StagePersistence.SavedAttempt saved) { this.version=saved.version(); this.highestHint=saved.highestHint(); this.playerResets=saved.playerResets(); this.systemRecoveryCount=saved.systemRecoveries(); this.commandSequence=saved.lastSequence(); }
        StageView view() { return new StageView(UUID.randomUUID().toString(), lastOutput, lastExitCode, snapshot, highestHint, playerResets, systemRecoveryCount, commandSequence, grade.cleared(), grade.stars(), grade.message()); }
    }
    record StageView(String requestId, String output, Integer exitCode, RepositorySnapshot snapshot, int hintLevel, int resetCount, int systemRecoveryCount, long commandSequence, boolean cleared, int stars, String gradeMessage) { }
    record StageProgress(String stageKey, String title, String summary, int highestStars) {
        public boolean isCleared() { return highestStars > 0; }
    }
}
