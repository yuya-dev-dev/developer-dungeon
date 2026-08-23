package jp.yuya.dev.developerdungeon.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
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

class StageFiveTemplateTest {
    @Test void rendersTheRedactedBranchBoardWithoutAnObjectId() throws Exception {
        StageService stages = mock(StageService.class);
        StageDefinition definition = new StageRules().definition("STAGE-GIT-05");
        StageView view = new StageView("request", "output", null, snapshot(), 0, 0, 0, 0, false, 0, "未復旧", List.of());
        when(stages.open("STAGE-GIT-05")).thenReturn(view);
        when(stages.definition("STAGE-GIT-05")).thenReturn(definition);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new StageController(stages)).setViewResolvers(viewResolver()).build();

        String rendered = mvc.perform(get("/stages/STAGE-GIT-05")
                        .requestAttr("_csrf", new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", "test-token")))
                .andReturn().getResponse().getContentAsString();

        assertThat(rendered).contains("<dt>main</dt><dd>存在</dd>",
                        "<dt>feature/payment-retry</dt><dd>未復旧</dd>")
                .doesNotContain("現在branch:", "HEAD:", "39194dda9576", "4b03c129e4d5");
    }

    private RepositorySnapshot snapshot() {
        return new RepositorySnapshot("4b03c129e4d5b2bfe41fb2afd208b13dab7824a1", "0".repeat(40), "", List.of(), true,
                false, List.of(), "main", "", "", false, false, false, RepositorySnapshot.StageThreeState.empty(),
                RepositorySnapshot.StageFourState.empty(), new RepositorySnapshot.StageFiveState(
                "4b03c129e4d5b2bfe41fb2afd208b13dab7824a1", "39194dda957695ace62387ecdc5f77fcd5ee81ea",
                "4b03c129e4d5b2bfe41fb2afd208b13dab7824a1", "c".repeat(40), null, List.of("main")));
    }

    private ThymeleafViewResolver viewResolver() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/"); resolver.setSuffix(".html"); resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding(StandardCharsets.UTF_8.name());
        SpringTemplateEngine engine = new SpringTemplateEngine(); engine.setTemplateResolver(resolver);
        ThymeleafViewResolver views = new ThymeleafViewResolver(); views.setTemplateEngine(engine);
        views.setCharacterEncoding(StandardCharsets.UTF_8.name()); return views;
    }
}
