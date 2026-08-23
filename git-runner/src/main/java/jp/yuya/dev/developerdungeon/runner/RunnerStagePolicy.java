package jp.yuya.dev.developerdungeon.runner;

import java.util.List;
import java.util.Set;
import jp.yuya.dev.developerdungeon.contract.CommandKind;
import jp.yuya.dev.developerdungeon.contract.GitCommand;
import jp.yuya.dev.developerdungeon.contract.RepositorySnapshot;

final class RunnerStagePolicy {
    private final String stageFourMainBlob;
    private final String stageFourMainTree;
    private final String stageFourFeatureTree;
    private final String stageFiveC0;
    private final String stageFiveC1;
    private final String stageFiveC1Tree;

    RunnerStagePolicy(String stageFourMainBlob, String stageFourMainTree, String stageFourFeatureTree,
            String stageFiveC0, String stageFiveC1, String stageFiveC1Tree) {
        this.stageFourMainBlob = stageFourMainBlob;
        this.stageFourMainTree = stageFourMainTree;
        this.stageFourFeatureTree = stageFourFeatureTree;
        this.stageFiveC0 = stageFiveC0;
        this.stageFiveC1 = stageFiveC1;
        this.stageFiveC1Tree = stageFiveC1Tree;
    }

    void validateCommand(String stageKey, GitCommand command, Targets targets) {
        if (stageKey.startsWith("TRAINING-GIT-")) {
            validateTrainingCommand(stageKey, command.kind());
            return;
        }
        if ("STAGE-GIT-01".equals(stageKey)) {
            requireAllowed(command.kind(), Set.of(CommandKind.STATUS, CommandKind.LOG_ONELINE, CommandKind.SHOW,
                    CommandKind.REVERT_NO_EDIT, CommandKind.REVERT_NO_COMMIT, CommandKind.COMMIT_RESTORE_SETTINGS));
            return;
        }
        if ("STAGE-GIT-04".equals(stageKey)) {
            requireAllowed(command.kind(), Set.of(CommandKind.STATUS, CommandKind.LOG_GRAPH_ALL, CommandKind.DIFF,
                    CommandKind.BRANCH, CommandKind.MERGE_PROFILE_MESSAGE, CommandKind.ADD_PROFILE_MESSAGES,
                    CommandKind.COMMIT_NO_EDIT, CommandKind.COMMIT_ALL_NO_EDIT));
            return;
        }
        if ("STAGE-GIT-05".equals(stageKey)) {
            requireAllowed(command.kind(), Set.of(CommandKind.STATUS, CommandKind.LOG_ONELINE_ALL_DECORATE,
                    CommandKind.REFLOG_HEAD, CommandKind.SHOW, CommandKind.CREATE_PAYMENT_RETRY_BRANCH,
                    CommandKind.SWITCH_PAYMENT_RETRY, CommandKind.SWITCH_CREATE_PAYMENT_RETRY));
            if ((command.kind() == CommandKind.CREATE_PAYMENT_RETRY_BRANCH
                    || command.kind() == CommandKind.SWITCH_CREATE_PAYMENT_RETRY)
                    && !command.objectId().equals(targets.recoveryTarget())) {
                throw new IllegalArgumentException("only the reflog recovery commit can be used");
            }
            return;
        }
        if ("STAGE-GIT-03".equals(stageKey)) {
            requireAllowed(command.kind(), Set.of(CommandKind.STATUS, CommandKind.DIFF, CommandKind.DIFF_STAGED,
                    CommandKind.BRANCH, CommandKind.STASH_PUSH, CommandKind.STASH_LIST, CommandKind.STASH_POP,
                    CommandKind.STASH_APPLY, CommandKind.STASH_DROP, CommandKind.SWITCH));
            if (command.kind() == CommandKind.SWITCH && !"feature/search".equals(command.branchName())) {
                throw new IllegalArgumentException("only the stage's search branch can be selected");
            }
            return;
        }
        if (!"STAGE-GIT-02".equals(stageKey)) {
            throw new IllegalArgumentException("command is not allowed for this stage");
        }
        requireAllowed(command.kind(), Set.of(CommandKind.STATUS, CommandKind.LOG_ONELINE_ALL_DECORATE,
                CommandKind.BRANCH, CommandKind.SHOW, CommandKind.SWITCH, CommandKind.CHERRY_PICK,
                CommandKind.RESET_HARD));
        if (command.kind() == CommandKind.CHERRY_PICK && !command.objectId().equals(targets.cherryPickTarget())) {
            throw new IllegalArgumentException("only the stage's notification commit can be cherry-picked");
        }
        if (command.kind() == CommandKind.RESET_HARD && !command.objectId().equals(targets.resetTarget())) {
            throw new IllegalArgumentException("only the stage's original branch tip can be reset");
        }
        if (command.kind() == CommandKind.SWITCH && !"feature/profile".equals(command.branchName())
                && !"feature/notification".equals(command.branchName())) {
            throw new IllegalArgumentException("only the stage's branches can be selected");
        }
    }

    void validateRevertTarget(GitCommand command, Targets targets) {
        if ((command.kind() == CommandKind.REVERT_NO_EDIT || command.kind() == CommandKind.REVERT_NO_COMMIT)
                && !command.objectId().equals(targets.revertTarget())) {
            throw new IllegalArgumentException("only the stage's accidental commit can be reverted");
        }
    }

    void validateTrainingCommand(String stageKey, CommandKind kind) {
        boolean allowed = switch (stageKey) {
            case "TRAINING-GIT-01" -> kind == CommandKind.STATUS || kind == CommandKind.DIFF
                    || kind == CommandKind.DIFF_STAGED || kind == CommandKind.LOG_ONELINE
                    || kind == CommandKind.ADD_TRAINING_INTRO || kind == CommandKind.COMMIT_TRAINING_ONE;
            case "TRAINING-GIT-02" -> kind == CommandKind.STATUS || kind == CommandKind.DIFF
                    || kind == CommandKind.DIFF_STAGED || kind == CommandKind.LOG_ONELINE
                    || kind == CommandKind.UNSTAGE_TRAINING_REPORT || kind == CommandKind.ADD_TRAINING_IGNORE
                    || kind == CommandKind.ADD_TRAINING_CONFIG || kind == CommandKind.COMMIT_TRAINING_TWO;
            case "TRAINING-GIT-03" -> kind == CommandKind.STATUS || kind == CommandKind.DIFF
                    || kind == CommandKind.DIFF_STAGED || kind == CommandKind.LOG_ONELINE || kind == CommandKind.BRANCH
                    || kind == CommandKind.SWITCH_CREATE_TRAINING_BRANCH || kind == CommandKind.SWITCH_TRAINING_BRANCH
                    || kind == CommandKind.ADD_TRAINING_HANDOFF || kind == CommandKind.COMMIT_TRAINING_THREE;
            default -> false;
        };
        if (!allowed) throw new IllegalArgumentException("command is not allowed for this training");
    }

    Targets captureTargets(String stageKey, RepositorySnapshot initial) {
        if (stageKey.startsWith("TRAINING-GIT-")) {
            validateTrainingInitial(stageKey, initial);
            return new Targets(null, null, null, null, Set.of(initial.headObjectId()));
        }
        if ("STAGE-GIT-01".equals(stageKey)) {
            return new Targets(initial.headObjectId(), null, null, null, Set.copyOf(initial.ancestorObjectIds()));
        }
        if ("STAGE-GIT-03".equals(stageKey)) {
            var state = initial.stageThree();
            if (!"main".equals(initial.currentBranch()) || !initial.headObjectId().equals(state.mainTip()) || state.mainTip().isBlank()
                    || state.featureSearchTip().isBlank() || !state.mainTip().equals(state.featureSearchParent()) || initial.clean()
                    || !state.workingTreePaths().equals(List.of("search.txt")) || !state.indexPaths().isEmpty() || !state.unmergedPaths().isEmpty()
                    || !state.untrackedPaths().isEmpty() || !state.stashObjectIds().isEmpty() || initial.revertInProgress()
                    || initial.cherryPickInProgress() || initial.mergeInProgress() || initial.rebaseInProgress()) {
                throw new IllegalStateException("stage fixture is invalid");
            }
            return Targets.empty();
        }
        if ("STAGE-GIT-04".equals(stageKey)) {
            var state = initial.stageFour();
            if (!"main".equals(initial.currentBranch()) || !initial.headObjectId().equals(state.mainTip())
                    || state.mainTip().isBlank() || state.mainParent().isBlank() || state.featureProfileMessageTip().isBlank()
                    || !state.mainParent().equals(state.featureProfileMessageParent())
                    || !stageFourMainBlob.equals(state.messagesBlobId())
                    || !stageFourMainTree.equals(state.mainTreeId()) || !stageFourFeatureTree.equals(state.featureTreeId())
                    || !initial.clean() || !state.workingTreePaths().isEmpty() || !state.indexPaths().isEmpty()
                    || !state.unmergedPaths().isEmpty() || !state.untrackedPaths().isEmpty() || initial.revertInProgress()
                    || initial.cherryPickInProgress() || initial.mergeInProgress() || initial.rebaseInProgress()) {
                throw new IllegalStateException("stage fixture is invalid");
            }
            return Targets.empty();
        }
        if ("STAGE-GIT-05".equals(stageKey)) {
            var state = initial.stageFive();
            if (!"main".equals(initial.currentBranch()) || !stageFiveC0.equals(initial.headObjectId())
                    || !stageFiveC0.equals(state.mainTip()) || !stageFiveC1.equals(state.recoveryTargetId())
                    || !stageFiveC0.equals(state.recoveryTargetParent()) || !stageFiveC1Tree.equals(state.recoveryTargetTreeId())
                    || state.paymentRetryTip() != null || !state.localBranches().equals(List.of("main")) || !initial.clean()
                    || initial.revertInProgress() || initial.cherryPickInProgress() || initial.mergeInProgress()
                    || initial.rebaseInProgress()) {
                throw new IllegalStateException("stage fixture is invalid");
            }
            return new Targets(null, null, null, stageFiveC1, Set.of(stageFiveC0, stageFiveC1));
        }
        if (!"STAGE-GIT-02".equals(stageKey)) throw new IllegalStateException("stage fixture is invalid");
        String c1 = initial.headObjectId();
        String c0 = initial.featureNotificationTip();
        if (!"feature/profile".equals(initial.currentBranch()) || !c1.equals(initial.featureProfileTip())
                || c0.isBlank() || initial.headParents().size() != 1 || !c0.equals(initial.headParents().getFirst())
                || !initial.clean() || initial.cherryPickInProgress()) {
            throw new IllegalStateException("stage fixture is invalid");
        }
        return new Targets(null, c0, c1, null, Set.of(c0, c1));
    }

    void validateTrainingInitial(String stageKey, RepositorySnapshot snapshot) {
        var state = snapshot.training();
        boolean common = "main".equals(snapshot.currentBranch()) && snapshot.headObjectId().equals(state.mainTip())
                && state.trainingBranchTip() == null && snapshot.headParents().isEmpty()
                && !snapshot.clean() && !snapshot.revertInProgress() && !snapshot.cherryPickInProgress()
                && !snapshot.mergeInProgress() && !snapshot.rebaseInProgress() && state.untrackedPaths().isEmpty();
        boolean valid = switch (stageKey) {
            case "TRAINING-GIT-01" -> common
                    && state.headPaths().equals(List.of("onboarding/intro.txt"))
                    && state.workingTreePaths().equals(List.of("onboarding/intro.txt"))
                    && state.indexPaths().isEmpty() && state.ignoredPaths().isEmpty() && !state.introBlobId().isBlank();
            case "TRAINING-GIT-02" -> common
                    && state.headPaths().equals(List.of(".gitignore", "config/application-training.properties"))
                    && state.workingTreePaths().equals(List.of(".gitignore", "config/application-training.properties"))
                    && state.indexPaths().equals(List.of("build/training-report.txt"))
                    && state.ignoredPaths().isEmpty() && state.reportExists()
                    && !state.ignoreBlobId().isBlank() && !state.configBlobId().isBlank() && !state.reportBlobId().isBlank();
            case "TRAINING-GIT-03" -> common && state.headPaths().equals(List.of("docs/handoff.md"))
                    && state.workingTreePaths().equals(List.of("docs/handoff.md"))
                    && state.indexPaths().isEmpty() && state.ignoredPaths().isEmpty() && !state.handoffBlobId().isBlank();
            default -> false;
        };
        if (!valid) throw new IllegalStateException("training fixture is invalid");
    }

    private void requireAllowed(CommandKind kind, Set<CommandKind> allowed) {
        if (!allowed.contains(kind)) throw new IllegalArgumentException("command is not allowed for this stage");
    }

    record Targets(String revertTarget, String resetTarget, String cherryPickTarget, String recoveryTarget,
                   Set<String> allowedObjects) {
        Targets {
            allowedObjects = Set.copyOf(allowedObjects);
        }
        static Targets empty() { return new Targets(null, null, null, null, Set.of()); }
    }
}
