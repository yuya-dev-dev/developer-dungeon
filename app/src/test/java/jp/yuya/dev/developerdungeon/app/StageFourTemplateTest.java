package jp.yuya.dev.developerdungeon.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.nio.charset.StandardCharsets;
import java.util.List;
import jp.yuya.dev.developerdungeon.contract.RepositorySnapshot;
import org.junit.jupiter.api.Test;
import org.springframework.security.web.csrf.DefaultCsrfToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.view.ThymeleafViewResolver;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

class StageFourTemplateTest {
    @Test void rendersOnlyTheFixedEscapedEditorWithASeparateWriteRequestId() throws Exception {
        StageService stages = mock(StageService.class);
        StageDefinition definition = new StageRules().definition("STAGE-GIT-04");
        StageView view = new StageView("command-request", "output", null, conflictedSnapshot(), 0, 0, 0, 0, false, 0, "未復旧", List.of());
        StageEditorView editor = new StageEditorView("</textarea><script>alert(1)</script>", "a".repeat(64), "write-request");
        when(stages.open("STAGE-GIT-04")).thenReturn(view);
        when(stages.definition("STAGE-GIT-04")).thenReturn(definition);
        when(stages.editor("STAGE-GIT-04")).thenReturn(editor);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new StageController(stages)).setViewResolvers(viewResolver()).build();

        String rendered = mvc.perform(get("/stages/STAGE-GIT-04")
                        .requestAttr("_csrf", new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", "test-token")))
                .andReturn().getResponse().getContentAsString();

        assertThat(rendered).contains("限定エディタ", "src/main/resources/messages.properties", "command-request", "write-request",
                "&lt;/textarea&gt;&lt;script&gt;alert(1)&lt;/script&gt;")
                .doesNotContain("</textarea><script>alert(1)</script>");
        verify(stages).editor("STAGE-GIT-04");
    }

    private RepositorySnapshot conflictedSnapshot() {
        String c0 = "0".repeat(40), c1 = "1".repeat(40), c2 = "2".repeat(40), tree = "3".repeat(40);
        return new RepositorySnapshot(c1, tree, tree, List.of(c0), false, false, List.of(c1, c0), "main", "", "", false, true,
                false, RepositorySnapshot.StageThreeState.empty(),
                new RepositorySnapshot.StageFourState(c1, c0, c2, c0, tree, tree, "4".repeat(40),
                        List.of(PATH), List.of(), List.of(PATH), List.of()));
    }

    private static final String PATH = "src/main/resources/messages.properties";

    private ThymeleafViewResolver viewResolver() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/"); resolver.setSuffix(".html"); resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding(StandardCharsets.UTF_8.name());
        SpringTemplateEngine engine = new SpringTemplateEngine(); engine.setTemplateResolver(resolver);
        ThymeleafViewResolver views = new ThymeleafViewResolver(); views.setTemplateEngine(engine);
        views.setCharacterEncoding(StandardCharsets.UTF_8.name()); return views;
    }
}
