package jp.yuya.dev.developerdungeon.app.javalearning.web;

import org.springframework.http.HttpStatus;
import jp.yuya.dev.developerdungeon.app.javalearning.application.JavaLearningService;
import jp.yuya.dev.developerdungeon.app.javalearning.domain.JavaDifficulty;
import jp.yuya.dev.developerdungeon.app.javalearning.domain.JavaProgressStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

@Controller
public class JavaLearningController {
    private final JavaLearningService learning;

    public JavaLearningController(JavaLearningService learning) {
        this.learning = learning;
    }

    @GetMapping("/java")
    String javaHome() {
        return "redirect:/java/problems";
    }

    @GetMapping("/java/problems")
    String problems(Model model) {
        model.addAttribute("problemGroups", java.util.List.of(
                new ProblemGroup("BEGINNER", "初級", "クラス数・フィールド・メソッドの指定に沿って、設計の型を覚えます。",
                        learning.list(JavaDifficulty.BEGINNER)),
                new ProblemGroup("INTERMEDIATE", "中級", "要求から必要な責務を読み取り、自分でクラス構成を決めます。",
                        learning.list(JavaDifficulty.INTERMEDIATE)),
                new ProblemGroup("ADVANCED", "上級", "実務に近い制約と例外を含む設計へ発展させます。",
                        learning.list(JavaDifficulty.ADVANCED))));
        return "java-problems";
    }

    @GetMapping("/java/problems/{slug}")
    String problem(@PathVariable String slug, Model model) {
        model.addAttribute("detail", learning.find(slug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)));
        model.addAttribute("statuses", JavaProgressStatus.values());
        return "java-problem";
    }

    @PostMapping("/java/problems/{slug}/progress")
    String progress(@PathVariable String slug, @RequestParam JavaProgressStatus status) {
        try {
            learning.update(slug, status);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return "redirect:/java/problems/" + slug;
    }

    record ProblemGroup(String name, String label, String description,
                        java.util.List<JavaLearningService.ProblemSummary> problems) { }
}
