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
    private final StageService stages;
    StageController(StageService stages) { this.stages = stages; }

    @GetMapping("/") String index(Model model) { model.addAttribute("stages", stages.progresses()); return "stages"; }
    @GetMapping("/stages/STAGE-GIT-01") String stageOne(Model model) { add(model, STAGE_ONE, stages.open(STAGE_ONE)); return "stage"; }
    @GetMapping("/stages/STAGE-GIT-02") String stageTwo(Model model) { add(model, STAGE_TWO, stages.open(STAGE_TWO)); return "stage"; }
    @PostMapping("/stages/STAGE-GIT-01/commands") String stageOneCommand(@RequestParam String command, @RequestParam String requestId, Model model) { add(model, STAGE_ONE, stages.execute(STAGE_ONE, command, requestId)); return "stage"; }
    @PostMapping("/stages/STAGE-GIT-02/commands") String stageTwoCommand(@RequestParam String command, @RequestParam String requestId, Model model) { add(model, STAGE_TWO, stages.execute(STAGE_TWO, command, requestId)); return "stage"; }
    @PostMapping("/stages/STAGE-GIT-01/hint") String stageOneHint(Model model) { add(model, STAGE_ONE, stages.hint(STAGE_ONE)); return "stage"; }
    @PostMapping("/stages/STAGE-GIT-02/hint") String stageTwoHint(Model model) { add(model, STAGE_TWO, stages.hint(STAGE_TWO)); return "stage"; }
    @PostMapping("/stages/STAGE-GIT-01/reset") String stageOneReset(Model model) { add(model, STAGE_ONE, stages.reset(STAGE_ONE)); return "stage"; }
    @PostMapping("/stages/STAGE-GIT-02/reset") String stageTwoReset(Model model) { add(model, STAGE_TWO, stages.reset(STAGE_TWO)); return "stage"; }

    private void add(Model model, String stageKey, StageView view) {
        model.addAttribute("view", view);
        model.addAttribute("stage", stages.definition(stageKey));
        model.addAttribute("actions", new StageActions("/stages/" + stageKey + "/commands", "/stages/" + stageKey + "/hint", "/stages/" + stageKey + "/reset"));
    }

    record StageActions(String commandPath, String hintPath, String resetPath) { }
}
