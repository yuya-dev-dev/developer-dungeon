package jp.yuya.dev.developerdungeon.app;

import java.util.List;
import java.util.Set;
import jp.yuya.dev.developerdungeon.contract.RepositorySnapshot;

final class StageStatePolicy {
    private static final String STAGE_THREE_INITIAL_BLOB = "80b018f3f86a4e710347ea98c0b903a1c6fcd9e7";
    private static final String STAGE_THREE_FINAL_BLOB = "0861e3929141f32f4e5c8bcd68fc03173a3e3c8e";
    private static final String STAGE_FOUR_PATH = "src/main/resources/messages.properties";
    private static final String STAGE_FOUR_MAIN_BLOB = "a6306bacd230ac74aaf017cde7717bc3eb83684c";
    private static final String STAGE_FOUR_FINAL_BLOB = "e9de6755c74ff6bee8b96abcccfec27a06f23881";
    private static final String STAGE_FOUR_MAIN_TREE = "63ec3ef493c5b54618798e50fe8d2e58bc40a4a9";
    private static final String STAGE_FOUR_FEATURE_TREE = "e4d8a76dfb74d699e48a7437d60811202ba7face";
    private static final String STAGE_FOUR_FINAL_TREE = "0c0ff72db8de95d04ed1169388a4f345c870d686";

    StageRules.StageTargets capture(StageDefinition definition, RepositorySnapshot snapshot) {
        if (definition.key().startsWith("TRAINING-GIT-")) {
            var state = snapshot.training();
            boolean common = "main".equals(snapshot.currentBranch()) && snapshot.headObjectId().equals(state.mainTip())
                    && state.trainingBranchTip() == null && snapshot.headParents().isEmpty() && !snapshot.clean()
                    && state.untrackedPaths().isEmpty() && !snapshot.revertInProgress()
                    && !snapshot.cherryPickInProgress() && !snapshot.mergeInProgress() && !snapshot.rebaseInProgress();
            boolean valid = switch (definition.key()) {
                case "TRAINING-GIT-01" -> common && state.headPaths().equals(List.of("onboarding/intro.txt"))
                        && state.workingTreePaths().equals(List.of("onboarding/intro.txt")) && state.indexPaths().isEmpty()
                        && state.ignoredPaths().isEmpty() && !state.introBlobId().isBlank();
                case "TRAINING-GIT-02" -> common
                        && state.headPaths().equals(List.of(".gitignore", "config/application-training.properties"))
                        && state.workingTreePaths().equals(List.of(".gitignore", "config/application-training.properties"))
                        && state.indexPaths().equals(List.of("build/training-report.txt")) && state.ignoredPaths().isEmpty()
                        && state.reportExists() && !state.ignoreBlobId().isBlank() && !state.configBlobId().isBlank()
                        && !state.reportBlobId().isBlank();
                case "TRAINING-GIT-03" -> common && state.headPaths().equals(List.of("docs/handoff.md"))
                        && state.workingTreePaths().equals(List.of("docs/handoff.md")) && state.indexPaths().isEmpty()
                        && state.ignoredPaths().isEmpty() && !state.handoffBlobId().isBlank();
                default -> false;
            };
            if (!valid) throw new IllegalStateException("invalid training fixture");
            return new StageRules.StageTargets(state.mainTip(), null, null, Set.of(state.mainTip()), state);
        }
        if ("STAGE-GIT-01".equals(definition.key())) {
            if (snapshot.headParents().isEmpty()) throw new IllegalStateException("invalid stage fixture");
            return new StageRules.StageTargets(snapshot.headObjectId(), null, snapshot.firstParentTreeId(), Set.copyOf(snapshot.ancestorObjectIds()));
        }
        if ("STAGE-GIT-03".equals(definition.key())) {
            var state = snapshot.stageThree();
            if (!"main".equals(snapshot.currentBranch()) || !snapshot.headObjectId().equals(state.mainTip())
                    || state.mainTip().isBlank() || state.featureSearchTip().isBlank() || !state.mainTip().equals(state.featureSearchParent())
                    || !STAGE_THREE_INITIAL_BLOB.equals(state.searchFileBlobId()) || snapshot.clean()
                    || !state.workingTreePaths().equals(List.of("search.txt")) || !state.indexPaths().isEmpty()
                    || !state.unmergedPaths().isEmpty() || !state.untrackedPaths().isEmpty() || !state.stashObjectIds().isEmpty()
                    || snapshot.revertInProgress() || snapshot.cherryPickInProgress() || snapshot.mergeInProgress() || snapshot.rebaseInProgress()) {
                throw new IllegalStateException("invalid stage fixture");
            }
            return new StageRules.StageTargets(state.mainTip(), state.featureSearchTip(), state.searchFileBlobId(), Set.of());
        }
        if ("STAGE-GIT-04".equals(definition.key())) {
            var state = snapshot.stageFour();
            if (!"main".equals(snapshot.currentBranch()) || !snapshot.headObjectId().equals(state.mainTip())
                    || state.mainTip().isBlank() || state.featureProfileMessageTip().isBlank()
                    || state.mainTip().equals(state.featureProfileMessageTip())
                    || snapshot.headParents().size() != 1 || !snapshot.headParents().getFirst().equals(state.mainParent())
                    || !state.mainParent().equals(state.featureProfileMessageParent())
                    || !STAGE_FOUR_MAIN_BLOB.equals(state.messagesBlobId())
                    || !STAGE_FOUR_MAIN_TREE.equals(state.mainTreeId()) || !STAGE_FOUR_FEATURE_TREE.equals(state.featureTreeId())
                    || !snapshot.clean() || !state.workingTreePaths().isEmpty() || !state.indexPaths().isEmpty()
                    || !state.unmergedPaths().isEmpty() || !state.untrackedPaths().isEmpty()
                    || snapshot.revertInProgress() || snapshot.cherryPickInProgress()
                    || snapshot.mergeInProgress() || snapshot.rebaseInProgress()) {
                throw new IllegalStateException("invalid stage fixture");
            }
            return new StageRules.StageTargets(state.mainTip(), state.featureProfileMessageTip(), STAGE_FOUR_FINAL_TREE, Set.of());
        }
        if ("STAGE-GIT-05".equals(definition.key())) {
            var state = snapshot.stageFive();
            if (!"main".equals(snapshot.currentBranch()) || !snapshot.headObjectId().equals(state.mainTip())
                    || state.mainTip().isBlank() || state.recoveryTargetId().isBlank() || state.recoveryTargetParent().isBlank()
                    || state.recoveryTargetTreeId().isBlank() || state.paymentRetryTip() != null || !state.localBranches().equals(List.of("main"))
                    || !snapshot.clean() || snapshot.revertInProgress() || snapshot.cherryPickInProgress()
                    || snapshot.mergeInProgress() || snapshot.rebaseInProgress()) {
                throw new IllegalStateException("invalid stage fixture");
            }
            return new StageRules.StageTargets(state.recoveryTargetId(), state.mainTip(), state.recoveryTargetTreeId(),
                    Set.of(state.recoveryTargetId(), state.mainTip()));
        }
        String c1 = snapshot.headObjectId();
        String c0 = snapshot.featureNotificationTip();
        if (!"feature/profile".equals(snapshot.currentBranch()) || !c1.equals(snapshot.featureProfileTip())
                || c0.isBlank() || snapshot.headParents().size() != 1 || !c0.equals(snapshot.headParents().getFirst())
                || !snapshot.clean() || snapshot.revertInProgress() || snapshot.cherryPickInProgress()) {
            throw new IllegalStateException("invalid stage fixture");
        }
        return new StageRules.StageTargets(c1, c0, snapshot.headTreeId(), Set.of(c1, c0));
    }
    void revealHintTargets(StageDefinition definition, int hintLevel, StageRules.StageTargets targets, Set<String> displayed) {
        if ("STAGE-GIT-01".equals(definition.key()) && hintLevel >= 4) {
            displayed.add(targets.primaryObjectId().substring(0, 12));
        }
        if ("STAGE-GIT-02".equals(definition.key()) && hintLevel >= 4) {
            displayed.add(targets.primaryObjectId().substring(0, 12));
            displayed.add(targets.secondaryObjectId().substring(0, 12));
        }
        if ("STAGE-GIT-05".equals(definition.key()) && hintLevel >= 4) displayed.add(targets.primaryObjectId().substring(0, 12));
    }
    List<String> hints(StageDefinition definition, int hintLevel, StageRules.StageTargets targets) {
        if (hintLevel == 0) return List.of();
        if ("TRAINING-GIT-01".equals(definition.key())) {
            if (hintLevel == 1) return List.of("working treeとindexの状態を確認し、案内文の差分を見てみよう。");
            if (hintLevel == 2) return List.of("変更を履歴へ記録する前に、次のcommitへ含める対象としてstageします。");
            if (hintLevel == 3) return List.of("git status、git diff、git add <file>、git diff --staged、git commit -m <message>、git log --onelineを使います。");
            return List.of("git add onboarding/intro.txt の後、git commit -m complete-training-01 を実行し、記録を確認しよう。");
        }
        if ("TRAINING-GIT-02".equals(definition.key())) {
            if (hintLevel == 1) return List.of("working treeとindexを比べ、成果物と再生成できるreportを分けよう。");
            if (hintLevel == 2) return List.of("誤ってstageしたfileはindexから外せます。.gitignoreと設定fileだけを選び直します。");
            if (hintLevel == 3) return List.of("git restore --staged <file>、git add <file>、git commit -m <message>を使います。");
            return List.of("git restore --staged build/training-report.txt の後、.gitignore と config/application-training.properties をaddし、git commit -m complete-training-02 を実行しよう。");
        }
        if ("TRAINING-GIT-03".equals(definition.key())) {
            if (hintLevel == 1) return List.of("現在branchと未commit変更を確認し、mainを動かさず作業場所を分けよう。");
            if (hintLevel == 2) return List.of("未commit変更は保持したまま、新しい作業branchを作成できます。");
            if (hintLevel == 3) return List.of("git switch -c <branch>、git add <file>、git commit -m <message>を使います。");
            return List.of("git switch -c feature/onboarding の後、docs/handoff.md をaddし、git commit -m complete-training-03 を実行しよう。");
        }
        if ("STAGE-GIT-01".equals(definition.key())) {
            if (hintLevel == 1) return List.of("まず履歴と作業ツリーを見比べ、どの変更の後から設定が消えたか確認しよう。");
            if (hintLevel == 2) return List.of("公開済みの履歴は消さず、対象の変更を打ち消す新しいcommitを積む方法を考えよう。");
            if (hintLevel == 3) return List.of("git status、git log --oneline、git show <commit-id>で根拠を確認する。復旧はgit revert --no-edit <commit-id>、またはgit revert --no-commit <commit-id>の後に固定messageでcommitする方法を選べる。");
            return List.of("取り消す対象は " + targets.primaryObjectId().substring(0, 12)
                    + "。git revert --no-edit " + targets.primaryObjectId().substring(0, 12)
                    + "、またはgit revert --no-commit " + targets.primaryObjectId().substring(0, 12)
                    + "の後にgit commit -m restore-required-settingsを実行すれば、どちらも共有履歴を残して復旧できる。");
        }
        if ("STAGE-GIT-03".equals(definition.key())) {
            if (hintLevel == 1) return List.of("まず作業ツリーとindexに、どの変更が残っているか観察しよう。");
            if (hintLevel == 2) return List.of("branchを切り替える前に、未commit変更を一時退避する方法を考えよう。");
            if (hintLevel == 3) return List.of("git stash pushで退避し、git switch feature/searchでbranchを移る。復元はgit stash pop、またはgit stash applyの後にgit stash dropでstashを空にする方法を選べる。");
            return List.of("git stash pushで退避し、git switch feature/searchへ移動する。その後はgit stash pop、またはgit stash applyとgit stash dropで検索機能の変更を戻し、stashを空にしよう。");
        }
        if ("STAGE-GIT-04".equals(definition.key())) {
            if (hintLevel == 1) return List.of("現在の状態と差分から、競合中のファイルと双方の変更を確認しよう。");
            if (hintLevel == 2) return List.of("片方を選ぶのではなく、security settingsとpublic profileの両方を残す文言を考えよう。");
            if (hintLevel == 3) return List.of("git merge <branch>で統合を始め、限定エディタで解消する。その後はgit add <file>とgit commit --no-edit、またはgit commit -a --no-editで確定できる。");
            return List.of("git merge feature/profile-messageの後、限定エディタへ `profile.description=Manage security settings and edit your public profile.` と入力する。git add "
                    + STAGE_FOUR_PATH + "の後にgit commit --no-edit、またはgit commit -a --no-editで統合を確定しよう。");
        }
        if ("STAGE-GIT-05".equals(definition.key())) {
            if (hintLevel == 1) return List.of("通常の履歴に目的の変更がないことを確かめ、HEADが以前指していた操作履歴を調べよう。");
            if (hintLevel == 2) return List.of("branch名がなくても、以前HEADが指したcommitは操作履歴に残ることがあります。");
            if (hintLevel == 3) return List.of("git reflog、git show <commit-id>で根拠を確認する。復旧はgit branch <branch> <commit-id>の後にswitchするか、git switch -c <branch> <commit-id>で同時に行える。");
            return List.of("C1は " + targets.primaryObjectId().substring(0, 12)
                    + "。git branch feature/payment-retry " + targets.primaryObjectId().substring(0, 12)
                    + "の後にgit switch feature/payment-retry、またはgit switch -c feature/payment-retry "
                    + targets.primaryObjectId().substring(0, 12) + "で復旧しよう。");
        }
        if (hintLevel == 1) return List.of("二つのbranchが現在どこを指し、どちらに通知機能の変更があるか比較しよう。");
        if (hintLevel == 2) return List.of("commitを移す操作と、未公開branchを元へ戻す操作を分けて考えよう。");
        if (hintLevel == 3) return List.of("git switch <branch>、git cherry-pick <commit-id>、git reset --hard <commit-id>を使う。C1を確認済みなら、変更を先に移す方法とprofileを先に戻す方法のどちらも選べる。");
        return List.of("C1は " + targets.primaryObjectId().substring(0, 12) + "、C0は " + targets.secondaryObjectId().substring(0, 12)
                + "。C1をfeature/notificationへcherry-pickしてからfeature/profileをC0へ戻すか、C1を確認したままfeature/profileを先にC0へ戻してからfeature/notificationへcherry-pickしよう。最後はfeature/notificationにいることを確認する。");
    }
    StageGrade grade(StageDefinition definition, RepositorySnapshot snapshot, StageRules.StageTargets targets, int highestHint, int playerResets) {
        boolean cleared;
        if (definition.key().startsWith("TRAINING-GIT-")) {
            var initial = targets.training();
            var state = snapshot.training();
            boolean common = snapshot.clean() && !snapshot.revertInProgress() && !snapshot.cherryPickInProgress()
                    && !snapshot.mergeInProgress() && !snapshot.rebaseInProgress()
                    && state.workingTreePaths().isEmpty() && state.indexPaths().isEmpty() && state.untrackedPaths().isEmpty();
            cleared = switch (definition.key()) {
                case "TRAINING-GIT-01" -> common && "main".equals(snapshot.currentBranch())
                        && snapshot.headParents().equals(List.of(initial.mainTip()))
                        && snapshot.headObjectId().equals(state.mainTip())
                        && state.trainingBranchTip() == null && state.headPaths().equals(List.of("onboarding/intro.txt"))
                        && initial.introBlobId().equals(state.introBlobId()) && state.ignoredPaths().isEmpty();
                case "TRAINING-GIT-02" -> common && "main".equals(snapshot.currentBranch())
                        && snapshot.headParents().equals(List.of(initial.mainTip()))
                        && snapshot.headObjectId().equals(state.mainTip()) && state.trainingBranchTip() == null
                        && state.headPaths().equals(List.of(".gitignore", "config/application-training.properties"))
                        && initial.ignoreBlobId().equals(state.ignoreBlobId()) && initial.configBlobId().equals(state.configBlobId())
                        && initial.reportBlobId().equals(state.reportBlobId()) && state.reportExists()
                        && state.ignoredPaths().equals(List.of("build/training-report.txt"));
                case "TRAINING-GIT-03" -> common && "feature/onboarding".equals(snapshot.currentBranch())
                        && initial.mainTip().equals(state.mainTip()) && snapshot.headParents().equals(List.of(initial.mainTip()))
                        && snapshot.headObjectId().equals(state.trainingBranchTip())
                        && state.headPaths().equals(List.of("docs/handoff.md"))
                        && initial.handoffBlobId().equals(state.handoffBlobId()) && state.ignoredPaths().isEmpty();
                default -> false;
            };
            if (!cleared) return new StageGrade(false, 0, "研修の完了条件はまだ満たしていません。");
            return new StageGrade(true, 1, "研修目標のrepository状態へ到達しました。");
        }
        if ("STAGE-GIT-01".equals(definition.key())) {
            cleared = snapshot.clean() && !snapshot.revertInProgress()
                    && snapshot.ancestorObjectIds().contains(targets.primaryObjectId())
                    && targets.expectedTreeId().equals(snapshot.headTreeId())
                    && snapshot.headParents().size() == 1 && snapshot.headParents().getFirst().equals(targets.primaryObjectId());
            if (!cleared) return new StageGrade(false, 0, "復旧条件はまだ満たしていません。");
            return new StageGrade(true, stars(highestHint, playerResets), "公開済みの誤commitを履歴に残したまま、安全に取り消せました。");
        }
        if ("STAGE-GIT-03".equals(definition.key())) {
            var state = snapshot.stageThree();
            cleared = !snapshot.clean() && !snapshot.revertInProgress() && !snapshot.cherryPickInProgress()
                    && !snapshot.mergeInProgress() && !snapshot.rebaseInProgress() && "feature/search".equals(snapshot.currentBranch())
                    && targets.primaryObjectId().equals(state.mainTip()) && targets.secondaryObjectId().equals(state.featureSearchTip())
                    && targets.secondaryObjectId().equals(snapshot.headObjectId()) && STAGE_THREE_FINAL_BLOB.equals(state.searchFileBlobId())
                    && state.workingTreePaths().equals(List.of("search.txt")) && state.indexPaths().isEmpty()
                    && state.unmergedPaths().isEmpty() && state.untrackedPaths().isEmpty() && state.stashObjectIds().isEmpty();
            if (!cleared) return new StageGrade(false, 0, "branch、作業ツリー、index、stashの状態をもう一度確認しましょう。");
            return new StageGrade(true, stars(highestHint, playerResets), "未commitの検索機能を正しいbranchへ移し、作業を整理できました。");
        }
        if ("STAGE-GIT-04".equals(definition.key())) {
            var state = snapshot.stageFour();
            cleared = snapshot.clean() && "main".equals(snapshot.currentBranch())
                    && !snapshot.revertInProgress() && !snapshot.cherryPickInProgress()
                    && !snapshot.mergeInProgress() && !snapshot.rebaseInProgress()
                    && snapshot.headParents().equals(List.of(targets.primaryObjectId(), targets.secondaryObjectId()))
                    && targets.expectedTreeId().equals(snapshot.headTreeId())
                    && snapshot.headObjectId().equals(state.mainTip())
                    && targets.primaryObjectId().equals(state.mainParent())
                    && targets.secondaryObjectId().equals(state.featureProfileMessageTip())
                    && STAGE_FOUR_FINAL_BLOB.equals(state.messagesBlobId())
                    && state.workingTreePaths().isEmpty() && state.indexPaths().isEmpty()
                    && state.unmergedPaths().isEmpty() && state.untrackedPaths().isEmpty();
            if (!cleared) return new StageGrade(false, 0, "merge commit、双方を残した文言、競合と作業ツリーの状態を確認しましょう。");
            return new StageGrade(true, stars(highestHint, playerResets), "双方の要件を残してコンフリクトを解消し、mainへ統合できました。");
        }
        if ("STAGE-GIT-05".equals(definition.key())) {
            var state = snapshot.stageFive();
            cleared = snapshot.clean() && !snapshot.revertInProgress() && !snapshot.cherryPickInProgress()
                    && !snapshot.mergeInProgress() && !snapshot.rebaseInProgress() && "feature/payment-retry".equals(snapshot.currentBranch())
                    && targets.primaryObjectId().equals(snapshot.headObjectId()) && targets.primaryObjectId().equals(state.paymentRetryTip())
                    && targets.secondaryObjectId().equals(state.mainTip()) && targets.primaryObjectId().equals(state.recoveryTargetId())
                    && targets.secondaryObjectId().equals(state.recoveryTargetParent()) && targets.expectedTreeId().equals(state.recoveryTargetTreeId())
                    && targets.expectedTreeId().equals(snapshot.headTreeId()) && state.localBranches().equals(List.of("feature/payment-retry", "main"));
            if (!cleared) return new StageGrade(false, 0, "復旧branch、main、作業ツリーの状態をもう一度確認しましょう。");
            return new StageGrade(true, stars(highestHint, playerResets), "操作履歴から根拠を確認し、mainを動かさずに消えたbranchを復旧できました。");
        }
        cleared = snapshot.clean() && !snapshot.revertInProgress() && !snapshot.cherryPickInProgress() && "feature/notification".equals(snapshot.currentBranch())
                && targets.secondaryObjectId().equals(snapshot.featureProfileTip())
                && !targets.secondaryObjectId().equals(snapshot.featureNotificationTip())
                && targets.expectedTreeId().equals(snapshot.headTreeId())
                && snapshot.headParents().size() == 1 && targets.secondaryObjectId().equals(snapshot.headParents().getFirst());
        if (!cleared) return new StageGrade(false, 0, "branch位置、履歴、作業ツリーの条件をもう一度確認しましょう。");
        return new StageGrade(true, stars(highestHint, playerResets), "未公開のcommitを正しいbranchへ移し、誤ったbranchを安全に戻せました。");
    }

    private int stars(int highestHint, int playerResets) { return highestHint >= 3 ? 1 : playerResets > 0 ? 2 : 3; }
}
