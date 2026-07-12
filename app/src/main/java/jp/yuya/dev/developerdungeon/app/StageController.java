package jp.yuya.dev.developerdungeon.app;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
class StageController {
    private final StageOneService stage;
    StageController(StageOneService stage) { this.stage = stage; }
    @GetMapping("/") String index(Model model) { model.addAttribute("stage", stage.progress()); return "stages"; }
    @GetMapping("/stages/STAGE-GIT-01") String play(Model model) { add(model, stage.open()); return "stage"; }
    @PostMapping("/commands") String command(@RequestParam String command, @RequestParam String requestId, Model model) { add(model, stage.execute(command, requestId)); return "stage"; }
    @PostMapping("/hint") String hint(Model model) { add(model, stage.hint()); return "stage"; }
    @PostMapping("/reset") String reset(Model model) { add(model, stage.reset()); return "stage"; }
    private void add(Model model, StageOneService.StageView view) { model.addAttribute("view", view); }
}
