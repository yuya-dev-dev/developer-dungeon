package jp.yuya.dev.developerdungeon.app.javalearning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import jp.yuya.dev.developerdungeon.app.javalearning.application.JavaLearningService;
import jp.yuya.dev.developerdungeon.app.javalearning.domain.JavaDifficulty;
import jp.yuya.dev.developerdungeon.app.javalearning.domain.JavaProblem;
import jp.yuya.dev.developerdungeon.app.javalearning.domain.JavaProgressStatus;
import jp.yuya.dev.developerdungeon.app.javalearning.web.JavaLearningController;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.view.ThymeleafViewResolver;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

class JavaLearningTemplateTest {
    @Test
    void rendersReadableListAndCollapsedReferenceSolution() throws Exception {
        JavaLearningService learning = mock(JavaLearningService.class);
        JavaProblem beginner = problem(JavaDifficulty.BEGINNER, "library-beginner", 1);
        JavaProblem intermediate = problem(JavaDifficulty.INTERMEDIATE, "library-intermediate", 4);
        JavaProblem advanced = problem(JavaDifficulty.ADVANCED, "library-advanced", 7);
        when(learning.list(JavaDifficulty.BEGINNER)).thenReturn(List.of(summary(beginner, JavaProgressStatus.NOT_STARTED)));
        when(learning.list(JavaDifficulty.INTERMEDIATE)).thenReturn(List.of(summary(intermediate, JavaProgressStatus.IN_PROGRESS)));
        when(learning.list(JavaDifficulty.ADVANCED)).thenReturn(List.of(summary(advanced, JavaProgressStatus.COMPLETED)));
        when(learning.find("library-beginner")).thenReturn(Optional.of(
                new JavaLearningService.ProblemDetail(beginner, JavaProgressStatus.NOT_STARTED)));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new JavaLearningController(learning))
                .setViewResolvers(viewResolver()).build();

        String list = mvc.perform(get("/java/problems")).andReturn().getResponse().getContentAsString();
        String detail = mvc.perform(get("/java/problems/library-beginner")).andReturn().getResponse().getContentAsString();

        assertThat(list).contains("Java Class Design", "初級", "中級", "上級", "未着手", "学習中", "完了",
                "href=\"/java/problems/library-beginner\"");
        assertThat(detail).contains("この問題で身につけること", "指定された設計の骨格", "模範設計例",
                "<details class=\"reference-file\"", "LibraryService.java", "class LibraryService", "&lt;script&gt;alert(1)&lt;/script&gt;")
                .doesNotContain("<script>alert(1)</script>");
    }

    private static JavaLearningService.ProblemSummary summary(JavaProblem problem, JavaProgressStatus status) {
        return new JavaLearningService.ProblemSummary(problem, status);
    }

    private static JavaProblem problem(JavaDifficulty difficulty, String slug, int order) {
        JavaProblem.BeginnerScaffold scaffold = difficulty == JavaDifficulty.BEGINNER
                ? new JavaProblem.BeginnerScaffold(1, List.of(new JavaProblem.ClassSpecification(
                "Book", "本を表す", 1, List.of("Book(String title)"), 1, 1,
                List.of("String title"), List.of("String getTitle()")))) : null;
        return new JavaProblem("KEY-" + order, slug, "図書館貸出", difficulty, order, "本の貸出を設計する", "概要",
                List.of("責務を分ける"), List.of("class"), List.of("本を貸し出す"), List.of("blankを拒否する"),
                List.of("状態を守る"), List.of("予約を追加する"), List.of("状態をどこへ置くか"), List.of("Bookを作る"),
                scaffold, List.of("LibraryService.java"), List.of(new JavaProblem.ReferenceSource(
                "LibraryService.java", "public final class LibraryService { String marker = \"<script>alert(1)</script>\"; }")));
    }

    private static ThymeleafViewResolver viewResolver() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/"); resolver.setSuffix(".html"); resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding(StandardCharsets.UTF_8.name());
        SpringTemplateEngine engine = new SpringTemplateEngine(); engine.setTemplateResolver(resolver);
        ThymeleafViewResolver views = new ThymeleafViewResolver(); views.setTemplateEngine(engine);
        views.setCharacterEncoding(StandardCharsets.UTF_8.name());
        return views;
    }
}
