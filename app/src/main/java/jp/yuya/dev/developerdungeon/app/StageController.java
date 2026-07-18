package jp.yuya.dev.developerdungeon.app;

import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
class StageController {
    private static final String STAGE_ONE = "STAGE-GIT-01";
    private static final String STAGE_TWO = "STAGE-GIT-02";
    private static final String STAGE_THREE = "STAGE-GIT-03";
    private static final String STAGE_FOUR = "STAGE-GIT-04";
    private static final String STAGE_FIVE = "STAGE-GIT-05";
    private static final List<CommandReference> COMMANDS = List.of(
            new CommandReference(1, "git status", "現在のbranchと作業ツリーの状態を確認する"),
            new CommandReference(2, "git diff", "未stageの変更内容を確認する"),
            new CommandReference(3, "git log --oneline", "commit履歴を短い形式で確認する"),
            new CommandReference(4, "git show <commit-id>", "特定commitの内容を確認する"),
            new CommandReference(5, "git switch <branch>", "作業するbranchを切り替える"),
            new CommandReference(6, "git stash push", "未commitの変更を一時退避する"),
            new CommandReference(7, "git stash pop", "一時退避した変更を適用してstashから削除する"),
            new CommandReference(8, "git stash apply", "一時退避した変更を適用し、stashにも残す"),
            new CommandReference(9, "git stash drop", "適用済みのstashを削除する"),
            new CommandReference(10, "git revert --no-edit <commit-id>", "共有履歴を残したまま変更を打ち消す"),
            new CommandReference(11, "git revert --no-commit <commit-id>", "打ち消す変更をcommitせずにstageする"),
            new CommandReference(12, "git commit -m restore-required-settings", "stage済みの復旧内容を固定messageでcommitする"),
            new CommandReference(13, "git cherry-pick <commit-id>", "特定commitの変更を現在のbranchへ適用する"),
            new CommandReference(14, "git reset --hard <commit-id>", "未共有のbranchを指定commitの状態へ戻す"),
            new CommandReference(15, "git merge <branch>", "別branchの変更を現在のbranchへ統合する"),
            new CommandReference(16, "git add <file>", "解消したファイルをstageする"),
            new CommandReference(17, "git commit --no-edit", "用意されたmessageで途中の操作を確定する"),
            new CommandReference(18, "git commit -a --no-edit", "追跡中の変更をstageして途中の操作を確定する"),
            new CommandReference(19, "git reflog", "HEADが過去に指していた履歴を確認する"),
            new CommandReference(20, "git branch <branch> <commit-id>", "指定commitを指すbranchを作成する"),
            new CommandReference(21, "git switch -c <branch> <commit-id>", "指定commitからbranchを作成して切り替える"));
    private final StageService stages;
    StageController(StageService stages) { this.stages = stages; }

    @GetMapping("/") String index() { return "title"; }
    @GetMapping("/git/stages") String stageList(Model model) {
        model.addAttribute("stages", stages.progresses().stream()
                .map(progress -> new StageListItem(Integer.parseInt(progress.stageKey().substring(progress.stageKey().length() - 2)),
                        progress.stageKey(), progress.title(), progress.isCleared()))
                .toList());
        return "stages";
    }
    @GetMapping("/commands") String commandList(Model model) { model.addAttribute("commands", COMMANDS); return "commands"; }
    @GetMapping("/stages/STAGE-GIT-01") String stageOne(Model model) { add(model, STAGE_ONE, stages.open(STAGE_ONE)); return "stage"; }
    @GetMapping("/stages/STAGE-GIT-02") String stageTwo(Model model) { add(model, STAGE_TWO, stages.open(STAGE_TWO)); return "stage"; }
    @GetMapping("/stages/STAGE-GIT-03") String stageThree(Model model) { add(model, STAGE_THREE, stages.open(STAGE_THREE)); return "stage"; }
    @GetMapping("/stages/STAGE-GIT-04") String stageFour(Model model) { add(model, STAGE_FOUR, stages.open(STAGE_FOUR)); return "stage"; }
    @GetMapping("/stages/STAGE-GIT-05") String stageFive(Model model) { add(model, STAGE_FIVE, stages.open(STAGE_FIVE)); return "stage"; }
    @PostMapping("/stages/STAGE-GIT-01/commands") String stageOneCommand(@RequestParam String command, @RequestParam String requestId, Model model) { add(model, STAGE_ONE, stages.execute(STAGE_ONE, command, requestId)); return "stage"; }
    @PostMapping("/stages/STAGE-GIT-02/commands") String stageTwoCommand(@RequestParam String command, @RequestParam String requestId, Model model) { add(model, STAGE_TWO, stages.execute(STAGE_TWO, command, requestId)); return "stage"; }
    @PostMapping("/stages/STAGE-GIT-03/commands") String stageThreeCommand(@RequestParam String command, @RequestParam String requestId, Model model) { add(model, STAGE_THREE, stages.execute(STAGE_THREE, command, requestId)); return "stage"; }
    @PostMapping("/stages/STAGE-GIT-04/commands") String stageFourCommand(@RequestParam String command, @RequestParam String requestId, Model model) { add(model, STAGE_FOUR, stages.execute(STAGE_FOUR, command, requestId)); return "stage"; }
    @PostMapping("/stages/STAGE-GIT-05/commands") String stageFiveCommand(@RequestParam String command, @RequestParam String requestId, Model model) { add(model, STAGE_FIVE, stages.execute(STAGE_FIVE, command, requestId)); return "stage"; }
    @PostMapping("/stages/STAGE-GIT-04/editor") String stageFourEditor(@RequestParam String content, @RequestParam String versionToken,
            @RequestParam String requestId, Model model) { add(model, STAGE_FOUR, stages.edit(STAGE_FOUR, content, versionToken, requestId)); return "stage"; }
    @PostMapping("/stages/STAGE-GIT-01/hint") String stageOneHint(Model model) { add(model, STAGE_ONE, stages.hint(STAGE_ONE)); return "stage"; }
    @PostMapping("/stages/STAGE-GIT-02/hint") String stageTwoHint(Model model) { add(model, STAGE_TWO, stages.hint(STAGE_TWO)); return "stage"; }
    @PostMapping("/stages/STAGE-GIT-03/hint") String stageThreeHint(Model model) { add(model, STAGE_THREE, stages.hint(STAGE_THREE)); return "stage"; }
    @PostMapping("/stages/STAGE-GIT-04/hint") String stageFourHint(Model model) { add(model, STAGE_FOUR, stages.hint(STAGE_FOUR)); return "stage"; }
    @PostMapping("/stages/STAGE-GIT-05/hint") String stageFiveHint(Model model) { add(model, STAGE_FIVE, stages.hint(STAGE_FIVE)); return "stage"; }
    @PostMapping("/stages/STAGE-GIT-01/reset") String stageOneReset(Model model) { add(model, STAGE_ONE, stages.reset(STAGE_ONE)); return "stage"; }
    @PostMapping("/stages/STAGE-GIT-02/reset") String stageTwoReset(Model model) { add(model, STAGE_TWO, stages.reset(STAGE_TWO)); return "stage"; }
    @PostMapping("/stages/STAGE-GIT-03/reset") String stageThreeReset(Model model) { add(model, STAGE_THREE, stages.reset(STAGE_THREE)); return "stage"; }
    @PostMapping("/stages/STAGE-GIT-04/reset") String stageFourReset(Model model) { add(model, STAGE_FOUR, stages.reset(STAGE_FOUR)); return "stage"; }
    @PostMapping("/stages/STAGE-GIT-05/reset") String stageFiveReset(Model model) { add(model, STAGE_FIVE, stages.reset(STAGE_FIVE)); return "stage"; }

    private void add(Model model, String stageKey, StageView view) {
        model.addAttribute("view", view);
        StageDefinition stage = stages.definition(stageKey);
        model.addAttribute("stage", stage);
        model.addAttribute("presentation", StagePresentationView.from(stage, view.snapshot()));
        boolean editorAvailable = STAGE_FOUR.equals(stageKey) && !view.cleared() && view.snapshot() != null
                && view.snapshot().mergeInProgress();
        model.addAttribute("editor", editorAvailable ? stages.editor(stageKey) : null);
        model.addAttribute("actions", new StageActions("/stages/" + stageKey + "/commands#stage-workspace",
                "/stages/" + stageKey + "/hint#stage-sidebar-hint",
                "/stages/" + stageKey + "/reset#stage-workspace",
                STAGE_FOUR.equals(stageKey) ? "/stages/STAGE-GIT-04/editor#stage-editor" : null));
    }

    record StageActions(String commandPath, String hintPath, String resetPath, String editorPath) { }
    record CommandReference(int number, String command, String purpose) { }
    record StageListItem(int number, String stageKey, String title, boolean cleared) { }
}
