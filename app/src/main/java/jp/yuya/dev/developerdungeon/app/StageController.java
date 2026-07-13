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
    private final StageService stages;
    StageController(StageService stages) { this.stages = stages; }

    @GetMapping("/") String index(Model model) { model.addAttribute("stages", stages.progresses()); return "stages"; }
    @GetMapping("/stages/STAGE-GIT-01") String stageOne(Model model) { add(model, STAGE_ONE, stages.open(STAGE_ONE)); return "stage"; }
    @GetMapping("/stages/STAGE-GIT-02") String stageTwo(Model model) { add(model, STAGE_TWO, stages.open(STAGE_TWO)); return "stage"; }
    @GetMapping("/stages/STAGE-GIT-03") String stageThree(Model model) { add(model, STAGE_THREE, stages.open(STAGE_THREE)); return "stage"; }
    @PostMapping("/stages/STAGE-GIT-01/commands") String stageOneCommand(@RequestParam String command, @RequestParam String requestId, Model model) { add(model, STAGE_ONE, stages.execute(STAGE_ONE, command, requestId)); return "stage"; }
    @PostMapping("/stages/STAGE-GIT-02/commands") String stageTwoCommand(@RequestParam String command, @RequestParam String requestId, Model model) { add(model, STAGE_TWO, stages.execute(STAGE_TWO, command, requestId)); return "stage"; }
    @PostMapping("/stages/STAGE-GIT-03/commands") String stageThreeCommand(@RequestParam String command, @RequestParam String requestId, Model model) { add(model, STAGE_THREE, stages.execute(STAGE_THREE, command, requestId)); return "stage"; }
    @PostMapping("/stages/STAGE-GIT-01/hint") String stageOneHint(Model model) { add(model, STAGE_ONE, stages.hint(STAGE_ONE)); return "stage"; }
    @PostMapping("/stages/STAGE-GIT-02/hint") String stageTwoHint(Model model) { add(model, STAGE_TWO, stages.hint(STAGE_TWO)); return "stage"; }
    @PostMapping("/stages/STAGE-GIT-03/hint") String stageThreeHint(Model model) { add(model, STAGE_THREE, stages.hint(STAGE_THREE)); return "stage"; }
    @PostMapping("/stages/STAGE-GIT-01/reset") String stageOneReset(Model model) { add(model, STAGE_ONE, stages.reset(STAGE_ONE)); return "stage"; }
    @PostMapping("/stages/STAGE-GIT-02/reset") String stageTwoReset(Model model) { add(model, STAGE_TWO, stages.reset(STAGE_TWO)); return "stage"; }
    @PostMapping("/stages/STAGE-GIT-03/reset") String stageThreeReset(Model model) { add(model, STAGE_THREE, stages.reset(STAGE_THREE)); return "stage"; }

    private void add(Model model, String stageKey, StageView view) {
        model.addAttribute("view", view);
        StageDefinition stage = stages.definition(stageKey);
        model.addAttribute("stage", stage);
        model.addAttribute("presentation", StagePresentationView.from(stage, view.snapshot()));
        model.addAttribute("actions", new StageActions("/stages/" + stageKey + "/commands", "/stages/" + stageKey + "/hint", "/stages/" + stageKey + "/reset"));
    }

    record StageActions(String commandPath, String hintPath, String resetPath) { }
}
