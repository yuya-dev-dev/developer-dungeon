package jp.yuya.dev.developerdungeon.app;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import jp.yuya.dev.developerdungeon.contract.CommandKind;
import jp.yuya.dev.developerdungeon.contract.GitCommand;
import jp.yuya.dev.developerdungeon.contract.RepositorySnapshot;
import org.springframework.stereotype.Component;

@Component
class StageRules {
    private static final Pattern OBJECT_ID = Pattern.compile("[0-9a-f]{12}([0-9a-f]{28})?");
    private static final Pattern LOG_ID = Pattern.compile("^([0-9a-f]{12})\\b.*");
    private static final StageDefinition STAGE_ONE = new StageDefinition("STAGE-GIT-01", "第1現場 / リリース障害", "公開済み変更を取り消す",
            "公開済みの誤変更を、履歴を壊さずに戻す。", "新人のあなたは、先輩から緊急チケットを受け取った。公開済みの誤変更を、履歴を壊さずに戻そう。",
            "誤ったcommitがmainへ公開された。履歴を消さず、利用者へ安全な取り消しを届けること。", "誤commitを履歴に残したまま、正常な状態へ戻す。",
            "git status / git log --oneline / git show <12または40桁ID> / git revert --no-edit <12または40桁ID>");
    private static final StageDefinition STAGE_TWO = new StageDefinition("STAGE-GIT-02", "第2現場 / branchの取り違え", "間違ったbranchのcommitを移す",
            "未公開の通知機能commitを、正しいbranchへ安全に移し直す。", "通知機能の変更が、誤って別の作業branchへcommitされている。先輩は、共有前に正しいbranchへ戻すよう依頼した。",
            "通知機能のcommitはfeature/profileにある。未公開のうちにfeature/notificationへ移し、profileを元の位置へ戻すこと。", "C1をnotificationへ移し、profileをC0へ戻してnotificationにいる。",
            "git status / git log --oneline --all --decorate / git branch / git show <12または40桁ID> / git switch <feature/profile|feature/notification> / git cherry-pick <12または40桁ID> / git reset --hard <12または40桁ID>");
    private static final Map<String, StageDefinition> DEFINITIONS = Map.of(STAGE_ONE.key(), STAGE_ONE, STAGE_TWO.key(), STAGE_TWO);

    List<StageDefinition> definitions() { return List.of(STAGE_ONE, STAGE_TWO); }
    StageDefinition definition(String stageKey) {
        StageDefinition definition = DEFINITIONS.get(stageKey);
        if (definition == null) throw new IllegalArgumentException("unknown stage");
        return definition;
    }
    GitCommand parse(StageDefinition definition, String raw) {
        rejectUnsafeRaw(raw);
        return switch (definition.key()) {
            case "STAGE-GIT-01" -> parseStageOne(raw);
            case "STAGE-GIT-02" -> parseStageTwo(raw);
            default -> throw new IllegalArgumentException("unknown stage");
        };
    }
    StageTargets capture(StageDefinition definition, RepositorySnapshot snapshot) {
        if ("STAGE-GIT-01".equals(definition.key())) {
            if (snapshot.headParents().isEmpty()) throw new IllegalStateException("invalid stage fixture");
            return new StageTargets(snapshot.headObjectId(), null, snapshot.firstParentTreeId(), Set.copyOf(snapshot.ancestorObjectIds()));
        }
        String c1 = snapshot.headObjectId();
        String c0 = snapshot.featureNotificationTip();
        if (!"feature/profile".equals(snapshot.currentBranch()) || !c1.equals(snapshot.featureProfileTip())
                || c0.isBlank() || snapshot.headParents().size() != 1 || !c0.equals(snapshot.headParents().getFirst())
                || !snapshot.clean() || snapshot.revertInProgress() || snapshot.cherryPickInProgress()) {
            throw new IllegalStateException("invalid stage fixture");
        }
        return new StageTargets(c1, c0, snapshot.headTreeId(), Set.of(c1, c0));
    }
    GitCommand normalize(StageDefinition definition, GitCommand command, StageTargets targets, Set<String> displayed) {
        if (command.objectId() == null) return command;
        String normalized = exactAllowedObject(command.objectId(), targets, displayed);
        if ("STAGE-GIT-01".equals(definition.key()) && command.kind() == CommandKind.REVERT_NO_EDIT && !normalized.equals(targets.primaryObjectId())) {
            throw new IllegalArgumentException("このステージで取り消せるcommitではありません。");
        }
        if ("STAGE-GIT-02".equals(definition.key())) {
            if (command.kind() == CommandKind.CHERRY_PICK && !normalized.equals(targets.primaryObjectId())) {
                throw new IllegalArgumentException("通知機能のcommitだけを移してください。");
            }
            if (command.kind() == CommandKind.RESET_HARD && !normalized.equals(targets.secondaryObjectId())) {
                throw new IllegalArgumentException("profileは元のC0へだけ戻してください。");
            }
        }
        return new GitCommand(command.kind(), normalized, null);
    }
    void recordDisplayedObjects(StageDefinition definition, GitCommand command, String output, StageTargets targets, Set<String> displayed) {
        if (command.kind() != CommandKind.LOG_ONELINE && command.kind() != CommandKind.LOG_ONELINE_ALL_DECORATE) return;
        output.lines().map(LOG_ID::matcher).filter(java.util.regex.Matcher::matches).map(matcher -> matcher.group(1))
                .filter(prefix -> targets.allowedObjects().stream().anyMatch(id -> id.startsWith(prefix))).forEach(displayed::add);
    }
    void revealHintTargets(StageDefinition definition, int hintLevel, StageTargets targets, Set<String> displayed) {
        if ("STAGE-GIT-02".equals(definition.key()) && hintLevel >= 4) {
            displayed.add(targets.primaryObjectId().substring(0, 12));
            displayed.add(targets.secondaryObjectId().substring(0, 12));
        }
    }
    List<String> hints(StageDefinition definition, int hintLevel, StageTargets targets) {
        if (hintLevel == 0) return List.of();
        if ("STAGE-GIT-01".equals(definition.key())) {
            if (hintLevel == 1) return List.of("まず履歴と作業ツリーの状態を観察しよう。");
            if (hintLevel == 2) return List.of("公開済みのcommitは、履歴を消すより取り消しcommitを積む方法を考えよう。");
            return List.of("対象commitを確認し、git revert --no-edit <commit-id>を使う。");
        }
        if (hintLevel == 1) return List.of("--all --decorateで、2つのbranchがどこを指すか比較しよう。");
        if (hintLevel == 2) return List.of("commitを移す操作と、未公開branchを元へ戻す操作を分けて考えよう。");
        if (hintLevel == 3) return List.of("feature/notificationへswitchし、C1をcherry-pickしてからprofileをC0へ戻す順序を考えよう。");
        return List.of("C1は " + targets.primaryObjectId().substring(0, 12) + "、C0は " + targets.secondaryObjectId().substring(0, 12)
                + "。notificationへswitchしてC1をcherry-pickし、profileへswitchしてC0へreset --hardし、notificationへ戻ろう。");
    }
    StageGrade grade(StageDefinition definition, RepositorySnapshot snapshot, StageTargets targets, int highestHint, int playerResets) {
        boolean cleared;
        if ("STAGE-GIT-01".equals(definition.key())) {
            cleared = snapshot.clean() && !snapshot.revertInProgress()
                    && snapshot.ancestorObjectIds().contains(targets.primaryObjectId())
                    && targets.expectedTreeId().equals(snapshot.headTreeId())
                    && snapshot.headParents().size() == 1 && snapshot.headParents().getFirst().equals(targets.primaryObjectId());
            if (!cleared) return new StageGrade(false, 0, "復旧条件はまだ満たしていません。");
            return new StageGrade(true, stars(highestHint, playerResets), "公開済みの誤commitを履歴に残したまま、安全に取り消せました。");
        }
        cleared = snapshot.clean() && !snapshot.revertInProgress() && !snapshot.cherryPickInProgress() && "feature/notification".equals(snapshot.currentBranch())
                && targets.secondaryObjectId().equals(snapshot.featureProfileTip())
                && !targets.secondaryObjectId().equals(snapshot.featureNotificationTip())
                && targets.expectedTreeId().equals(snapshot.headTreeId())
                && snapshot.headParents().size() == 1 && targets.secondaryObjectId().equals(snapshot.headParents().getFirst());
        if (!cleared) return new StageGrade(false, 0, "branch位置、履歴、作業ツリーの条件をもう一度確認しましょう。");
        return new StageGrade(true, stars(highestHint, playerResets), "未公開のcommitを正しいbranchへ移し、誤ったbranchを安全に戻せました。");
    }

    private GitCommand parseStageOne(String raw) {
        if ("git status".equals(raw)) return new GitCommand(CommandKind.STATUS);
        if ("git log --oneline".equals(raw)) return new GitCommand(CommandKind.LOG_ONELINE);
        if (raw.matches("git show " + OBJECT_ID.pattern())) return new GitCommand(CommandKind.SHOW, raw.substring(9));
        if (raw.matches("git revert --no-edit " + OBJECT_ID.pattern())) return new GitCommand(CommandKind.REVERT_NO_EDIT, raw.substring(21));
        throw new IllegalArgumentException("このステージで許可されたGitコマンドではありません。");
    }
    private GitCommand parseStageTwo(String raw) {
        if ("git status".equals(raw)) return new GitCommand(CommandKind.STATUS);
        if ("git log --oneline --all --decorate".equals(raw)) return new GitCommand(CommandKind.LOG_ONELINE_ALL_DECORATE);
        if ("git branch".equals(raw)) return new GitCommand(CommandKind.BRANCH);
        if (raw.matches("git show " + OBJECT_ID.pattern())) return new GitCommand(CommandKind.SHOW, raw.substring(9));
        if (raw.matches("git switch feature/(profile|notification)")) return GitCommand.switchTo(raw.substring(11));
        if (raw.matches("git cherry-pick " + OBJECT_ID.pattern())) return new GitCommand(CommandKind.CHERRY_PICK, raw.substring(16));
        if (raw.matches("git reset --hard " + OBJECT_ID.pattern())) return new GitCommand(CommandKind.RESET_HARD, raw.substring(17));
        throw new IllegalArgumentException("このステージで許可されたGitコマンドではありません。");
    }
    private void rejectUnsafeRaw(String raw) {
        if (raw == null || raw.length() > 512 || raw.indexOf('\n') >= 0 || raw.indexOf('\r') >= 0
                || raw.matches(".*[;|&<>`].*") || raw.contains("$()") || raw.contains("\"") || raw.contains("'")) {
            throw new IllegalArgumentException("許可されていない入力です。");
        }
    }
    private String exactAllowedObject(String input, StageTargets targets, Set<String> displayed) {
        List<String> candidates = targets.allowedObjects().stream().filter(id -> id.startsWith(input)).toList();
        if (candidates.size() != 1 || !displayed.contains(candidates.getFirst().substring(0, 12))) {
            throw new IllegalArgumentException("表示済みの一意なcommit IDだけを指定してください。");
        }
        return candidates.getFirst();
    }
    private int stars(int highestHint, int playerResets) { return highestHint >= 3 ? 1 : playerResets > 0 ? 2 : 3; }

    record StageTargets(String primaryObjectId, String secondaryObjectId, String expectedTreeId, Set<String> allowedObjects) {
        StageTargets { allowedObjects = Set.copyOf(new LinkedHashSet<>(allowedObjects)); }
    }
}
