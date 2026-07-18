package jp.yuya.dev.developerdungeon.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.view.ThymeleafViewResolver;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

class CommandReferenceTemplateTest {
    @Test void rendersTheFixedThreeColumnCommandCatalogWithoutCallingTheService() throws Exception {
        StageService stages = mock(StageService.class);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new StageController(stages)).setViewResolvers(viewResolver()).build();

        String rendered = mvc.perform(get("/commands")).andReturn().getResponse().getContentAsString();

        assertThat(rendered).contains("番号", "コマンド", "用途", "git status", "git revert --no-edit &lt;commit-id&gt;",
                "git revert --no-commit &lt;commit-id&gt;", "git stash apply", "git commit -a --no-edit",
                "git commit -m restore-required-settings", "git switch -c &lt;branch&gt; &lt;commit-id&gt;",
                "href=\"/git/stages\"");
        assertThat(rendered.split("<tr>", -1)).hasSize(23);
        verifyNoInteractions(stages);
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
