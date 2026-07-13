package jp.yuya.dev.developerdungeon.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.security.web.csrf.DefaultCsrfToken;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.view.ThymeleafViewResolver;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

class StageResultTemplateTest {
    @Test void rendersDistinctStageOutcomes() {
        String stageOne = render("STAGE-GIT-01", true);
        String stageTwo = render("STAGE-GIT-02", true);
        String stageThree = render("STAGE-GIT-03", true);
        String stageFour = render("STAGE-GIT-04", true);

        assertThat(stageOne).contains("公開済みのmainに、必要な設定を削除する誤commitが含まれていました。",
                "mainには誤commitが履歴として残り", "revertは公開済みの履歴を変えずに", "reset --hardで公開済みcommitを消すと",
                "公開済み履歴を壊さずに復旧できたと判断するには", "誤commitが履歴に残り", "公開済みの履歴を消さずに戻せたね",
                "主人公は、最短に見える操作ではなく", "復旧根拠を確認する", "根拠と解説を開く");
        assertThat(stageOne).doesNotContain("未公開の通知機能commitがfeature/profileに置かれ", "feature/notificationが通知機能を持つ新しいcommitを指し");
        assertThat(stageTwo).contains("未公開の通知機能commitがfeature/profileに置かれ、正しいfeature/notificationにはまだありませんでした。",
                "feature/notificationが通知機能を持つ新しいcommitを指し", "通知機能を先にcherry-pickしてからprofileを戻すことで",
                "通知機能を移す前にfeature/profileをresetすると", "通知機能を正しいbranchへ移し", "feature/profileが元のC0へ戻り",
                "共有前に気づけたのは大きい", "commitの内容だけでなく", "復旧根拠を確認する", "根拠と解説を開く");
        assertThat(stageTwo).doesNotContain("公開済みのmainに、必要な設定を削除する誤commitが含まれていました。", "mainには誤commitが履歴として残り");
        assertThat(stageThree).contains("main上の未commitな検索機能の変更が、正しいfeature/searchではなく作業ツリーに残っていました。",
                "mainとfeature/searchのcommit位置を変えずに", "stashで作業中の変更を一時退避してからbranchを切り替えると",
                "変更を残したままbranchを切り替えようとすると", "検索機能の作業を失わず正しいbranchへ移せたと判断するには",
                "feature/search上で検索機能の変更だけが未commitで残り", "作業を急いでcommitしなくても", "作業中の変更を失わずに整理し");
        assertThat(stageThree).doesNotContain("公開済みのmainに、必要な設定を削除する誤commitが含まれていました。", "未公開の通知機能commitがfeature/profileに置かれ");
        assertThat(stageFour).contains("mainとfeature/profile-messageが、同じプロフィール説明文を異なる目的で変更していました。",
                "security settingsとpublic profileの両方", "片方を選ぶのではなく要件を統合し", "oursまたはtheirsだけを採用すると",
                "双方の意図を残して統合できたと判断するには", "merge commitがmainとfeatureの両方を直接parentに持ち");
    }

    @Test void selfCheckIsDisplayOnlyAndDoesNotRenderBeforeClear() {
        String cleared = render("STAGE-GIT-01", true);
        String active = render("STAGE-GIT-01", false);
        String result = cleared.substring(cleared.indexOf("<section class=\"result\""));

        assertThat(result).contains("<details>", "復旧根拠を確認する").doesNotContain("<form", "method=\"post\"");
        assertThat(active).doesNotContain("復旧根拠を確認する", "何が壊れていたか", "修復後どうなったか", "なぜこの方法が安全か", "避けるべき選択",
                "公開済みのmainに、必要な設定を削除する誤commitが含まれていました。", "mainには誤commitが履歴として残り");
    }

    private String render(String stageKey, boolean cleared) {
        StageService stages = mock(StageService.class);
        StageDefinition definition = new StageRules().definition(stageKey);
        StageView view = new StageView("request", "output", null, null, 0, 0, 0, 0, cleared, cleared ? 3 : 0,
                cleared ? "復旧しました。" : "未復旧", List.of());
        when(stages.open(stageKey)).thenReturn(view);
        when(stages.definition(stageKey)).thenReturn(definition);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new StageController(stages)).setViewResolvers(viewResolver()).build();

        try {
            String rendered = mvc.perform(get("/stages/" + stageKey)
                    .requestAttr("_csrf", new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", "test-token")))
                    .andReturn().getResponse().getContentAsString();
            verify(stages).open(stageKey);
            verify(stages).definition(stageKey);
            verifyNoMoreInteractions(stages);
            return rendered;
        } catch (Exception exception) {
            throw new AssertionError("stage result template did not render", exception);
        }
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
