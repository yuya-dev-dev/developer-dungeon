package jp.yuya.dev.developerdungeon.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.view.ThymeleafViewResolver;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

class StagesTemplateTest {
    @Test void rendersTheTitleAndFixedGitStageListWithoutStarsOrSummaries() {
        String title = renderTitle();
        String rendered = render(0, 3, 0, 0, 0);

        assertThat(title).contains("Developer", "Dungeon", "新人エンジニア研修シミュレーション", "Git編", "href=\"/git/stages\"");
        assertThat(rendered).contains("STAGE", ">01<", ">02<", ">03<", ">04<", ">05<", "未クリア", "CLEAR",
                "公開済み変更を取り消す", "消えたcommitを取り戻す")
                .doesNotContain("最高スター", "★", "summary");
    }

    private String renderTitle() {
        StageService stages = mock(StageService.class);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new StageController(stages)).setViewResolvers(viewResolver()).build();
        try { return mvc.perform(get("/")).andReturn().getResponse().getContentAsString(); }
        catch (Exception exception) { throw new AssertionError("title template did not render", exception); }
    }

    private String render(int stageOneStars, int stageTwoStars, int stageThreeStars, int stageFourStars, int stageFiveStars) {
        StageService stages = mock(StageService.class);
        when(stages.progresses()).thenReturn(List.of(new StageProgress("STAGE-GIT-01", "公開済み変更を取り消す", "summary", stageOneStars),
                new StageProgress("STAGE-GIT-02", "間違ったbranchのcommitを移す", "summary", stageTwoStars),
                new StageProgress("STAGE-GIT-03", "未commitの作業を正しいbranchへ移す", "summary", stageThreeStars),
                new StageProgress("STAGE-GIT-04", "コンフリクトを解消して統合する", "summary", stageFourStars),
                new StageProgress("STAGE-GIT-05", "消えたcommitを取り戻す", "summary", stageFiveStars)));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new StageController(stages)).setViewResolvers(viewResolver()).build();
        try { return mvc.perform(get("/git/stages")).andReturn().getResponse().getContentAsString(); }
        catch (Exception exception) { throw new AssertionError("stage list template did not render", exception); }
    }

    private ThymeleafViewResolver viewResolver() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/"); resolver.setSuffix(".html"); resolver.setTemplateMode(TemplateMode.HTML); resolver.setCharacterEncoding(StandardCharsets.UTF_8.name());
        SpringTemplateEngine engine = new SpringTemplateEngine(); engine.setTemplateResolver(resolver);
        ThymeleafViewResolver views = new ThymeleafViewResolver(); views.setTemplateEngine(engine); views.setCharacterEncoding(StandardCharsets.UTF_8.name()); return views;
    }
}
