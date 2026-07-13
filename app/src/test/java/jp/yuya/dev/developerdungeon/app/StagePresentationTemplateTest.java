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
    @Test void keepsGuidanceAndIncidentBoardIndependentAcrossAllCombinations() {
        assertPresentation(StagePresentationPolicy.GuidanceMode.FULL_SYNTAX, StagePresentationPolicy.IncidentBoardMode.BASIC, true, true);
        assertPresentation(StagePresentationPolicy.GuidanceMode.FULL_SYNTAX, StagePresentationPolicy.IncidentBoardMode.OFF, true, false);
        assertPresentation(StagePresentationPolicy.GuidanceMode.CONCEPT_ONLY, StagePresentationPolicy.IncidentBoardMode.BASIC, false, true);
        assertPresentation(StagePresentationPolicy.GuidanceMode.CONCEPT_ONLY, StagePresentationPolicy.IncidentBoardMode.OFF, false, false);
    }

    @Test void stageThreeHidesExactSyntaxUntilHintThree() {
        StageDefinition stage = new StageRules().definition("STAGE-GIT-03");
        String before = render(stage, List.of());
        String after = render(stage, List.of("git stash push、git switch <branch>、git stash popの形を順に使う。"));

        assertThat(before).contains("使う考え方:", "観察 / ", "一時退避 / ", "branch移動", "Gitコマンドを入力")
                .doesNotContain("git stash push", "git diff --staged", "incident-board");
        assertThat(after).contains("git stash push", "git switch &lt;branch&gt;", "git stash pop");
    }

    private void assertPresentation(StagePresentationPolicy.GuidanceMode guidance, StagePresentationPolicy.IncidentBoardMode board,
                                    boolean expectsSyntax, boolean expectsBoard) {
        StageDefinition stage = new StageDefinition("STAGE-GIT-01", "chapter", "title", "summary", "intro", "ticket", "objective", "EXACT COMMANDS", outcome(),
                new StagePresentationPolicy(guidance, board, List.of("観察")));
        String rendered = render(stage, List.of());
        if (expectsSyntax) assertThat(rendered).contains("EXACT COMMANDS");
        else assertThat(rendered).contains("使う考え方:", "観察").doesNotContain("EXACT COMMANDS");
        if (expectsBoard) assertThat(rendered).contains("class=\"incident-board\"");
        else assertThat(rendered).doesNotContain("class=\"incident-board\"");
    }

    private String render(StageDefinition stage, List<String> hints) {
        StageService stages = mock(StageService.class);
        var snapshot = new jp.yuya.dev.developerdungeon.contract.RepositorySnapshot("a".repeat(40), "tree", "", List.of(), true, false, List.of(), "main", "", "", false);
        var view = new StageView("request", "output", null, snapshot, hints.isEmpty() ? 0 : 3, 0, 0, 0, false, 0, "未復旧", hints);
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
