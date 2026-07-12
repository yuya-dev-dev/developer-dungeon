package jp.yuya.dev.developerdungeon.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.spring6.view.ThymeleafViewResolver;

class StagesTemplateTest {
    @Test void rendersUnclearedStageWithoutStars() {
        String rendered = render(0);

        assertThat(rendered).contains("未クリア", "最高スター: 未記録").doesNotContain("クリア済み");
    }
    @Test void rendersClearedStageWithHighestStars() {
        String rendered = render(3);

        assertThat(rendered).contains("クリア済み", "最高スター: <strong><span>3</span> ★</strong>");
    }

    private String render(int highestStars) {
        StageOneService stage = mock(StageOneService.class);
        when(stage.progress()).thenReturn(new StageOneService.StageProgress("STAGE-GIT-01", "公開済み変更を取り消す", "summary", highestStars));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new StageController(stage)).setViewResolvers(viewResolver()).build();
        try {
            return mvc.perform(get("/")).andReturn().getResponse().getContentAsString();
        } catch (Exception exception) {
            throw new AssertionError("stage list template did not render", exception);
        }
    }

    private ThymeleafViewResolver viewResolver() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding(StandardCharsets.UTF_8.name());
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        ThymeleafViewResolver views = new ThymeleafViewResolver();
        views.setTemplateEngine(engine);
        views.setCharacterEncoding(StandardCharsets.UTF_8.name());
        return views;
    }
}
