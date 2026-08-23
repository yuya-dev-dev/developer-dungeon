package jp.yuya.dev.developerdungeon.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.web.csrf.DefaultCsrfToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.view.ThymeleafViewResolver;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

class StagePresentationTemplateTest {
    @Test void keepsIncidentBoardIndependentFromTheRemovedGuidancePanel() {
        assertPresentation(StagePresentationPolicy.GuidanceMode.FULL_SYNTAX, StagePresentationPolicy.IncidentBoardMode.BASIC, true);
        assertPresentation(StagePresentationPolicy.GuidanceMode.FULL_SYNTAX, StagePresentationPolicy.IncidentBoardMode.OFF, false);
        assertPresentation(StagePresentationPolicy.GuidanceMode.CONCEPT_ONLY, StagePresentationPolicy.IncidentBoardMode.BASIC, true);
        assertPresentation(StagePresentationPolicy.GuidanceMode.CONCEPT_ONLY, StagePresentationPolicy.IncidentBoardMode.OFF, false);
    }

    @Test void stageThreeHidesExactSyntaxUntilHintThree() {
        StageDefinition stage = new StageRules().definition("STAGE-GIT-03");
        String before = render(stage, List.of());
        String after = render(stage, List.of("git stash push、git switch <branch>、git stash popの形を順に使う。"));

        assertThat(before).contains("Gitコマンドを入力", "ヒントを見る <span>0</span>/4")
                .doesNotContain("git stash push", "git diff --staged", "incident-board", "concept-chips");
        assertThat(after).contains("git stash push", "git switch &lt;branch&gt;", "git stash pop");
    }

    private void assertPresentation(StagePresentationPolicy.GuidanceMode guidance, StagePresentationPolicy.IncidentBoardMode board,
                                    boolean expectsBoard) {
        StageDefinition stage = new StageDefinition("STAGE-GIT-01", "chapter", "title", "summary", "intro", "ticket", "objective", "EXACT COMMANDS", outcome(),
                new StagePresentationPolicy(guidance, board, List.of("観察")));
        String rendered = render(stage, List.of());
        assertThat(rendered).doesNotContain("EXACT COMMANDS", "調査・対応の観点", "class=\"concept-chips\"");
        if (expectsBoard) assertThat(rendered).contains("class=\"incident-board\"");
        else assertThat(rendered).doesNotContain("class=\"incident-board\"");
    }

    @Test void rendersFeedbackKindsWithoutInferringThemFromExitCode() {
        StageDefinition stage = new StageRules().definition("STAGE-GIT-03");

        assertThat(render(stage, List.of(), StageFeedbackKind.INPUT_REJECTED, null, "構文を確認してください"))
                .contains("feedback-input_rejected", "入力を確認してください");
        assertThat(render(stage, List.of(), StageFeedbackKind.GIT_ERROR, 1, "fatal"))
                .contains("feedback-git_error", "Gitからエラーが返されました", "exit code:", ">1<");
        assertThat(render(stage, List.of(), StageFeedbackKind.SYSTEM_ERROR, null, "Runnerへ接続できません"))
                .contains("feedback-system_error", "システムからのお知らせ");
        assertThat(render(stage, List.of(), StageFeedbackKind.EDIT_CONFLICT, 1, "別の操作で更新されました"))
                .contains("feedback-edit_conflict", "ファイルを再読み込みしてください")
                .doesNotContain("Gitからエラーが返されました");
        assertThat(render(stage, List.of(), StageFeedbackKind.SUCCEEDED, 0, "ok"))
                .contains("feedback-succeeded", "実行結果", "exit code:", ">0<");
    }

    private String render(StageDefinition stage, List<String> hints) {
        return render(stage, hints, StageFeedbackKind.INITIAL, null, "output");
    }

    private String render(StageDefinition stage, List<String> hints, StageFeedbackKind feedbackKind, Integer exitCode, String output) {
        StageService stages = mock(StageService.class);
        var snapshot = new jp.yuya.dev.developerdungeon.contract.RepositorySnapshot("a".repeat(40), "tree", "", List.of(), true, false, List.of(), "main", "", "", false);
        var view = new StageView("request", output, exitCode, feedbackKind, snapshot, hints.isEmpty() ? 0 : 3, 0, 0, 0, false, 0, "未復旧", hints);
        when(stages.open(stage.key())).thenReturn(view);
        when(stages.definition(stage.key())).thenReturn(stage);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new StageController(stages)).setViewResolvers(viewResolver()).build();
        try {
            String rendered = mvc.perform(get("/stages/" + stage.key()).requestAttr("_csrf", new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", "test-token")))
                    .andReturn().getResponse().getContentAsString();
            verify(stages).open(stage.key()); verify(stages).definition(stage.key());
            return rendered;
        } catch (Exception exception) { throw new AssertionError("presentation template did not render", exception); }
    }

    private static StageOutcome outcome() { return new StageOutcome("incident", "repaired", "safe", "unsafe", "prompt", "explanation", "scene", "growth"); }
    private ThymeleafViewResolver viewResolver() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/"); resolver.setSuffix(".html"); resolver.setTemplateMode(TemplateMode.HTML); resolver.setCharacterEncoding(StandardCharsets.UTF_8.name());
        SpringTemplateEngine engine = new SpringTemplateEngine(); engine.setTemplateResolver(resolver);
        ThymeleafViewResolver views = new ThymeleafViewResolver(); views.setTemplateEngine(engine); views.setCharacterEncoding(StandardCharsets.UTF_8.name()); return views;
    }
}
