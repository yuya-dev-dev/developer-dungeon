package jp.yuya.dev.developerdungeon.app;

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
    private final StageService stages;
    StageController(StageService stages) { this.stages = stages; }

    @GetMapping("/") String index(Model model) { model.addAttribute("stages", stages.progresses()); return "stages"; }
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
        model.addAttribute("editor", STAGE_FOUR.equals(stageKey) && !view.cleared() ? stages.editor(stageKey) : null);
        model.addAttribute("actions", new StageActions("/stages/" + stageKey + "/commands", "/stages/" + stageKey + "/hint",
                "/stages/" + stageKey + "/reset", STAGE_FOUR.equals(stageKey) ? "/stages/STAGE-GIT-04/editor" : null));
    }

    record StageActions(String commandPath, String hintPath, String resetPath, String editorPath) { }
}
