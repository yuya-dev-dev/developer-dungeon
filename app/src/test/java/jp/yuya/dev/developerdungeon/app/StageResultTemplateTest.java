package jp.yuya.dev.developerdungeon.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.nio.charset.StandardCharsets;
import java.util.List;
import jp.yuya.dev.developerdungeon.contract.RepositorySnapshot;
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
                "公開済み履歴を壊さずに復旧できたと判断するには", "誤commitが履歴に残り", "共有履歴を守る判断ができたね",
                "主人公は、最短に見える操作ではなく", "復旧根拠を確認する", "根拠と解説を開く");
        assertThat(stageOne).doesNotContain("未公開の通知機能commitがfeature/profileに置かれ", "feature/notificationが通知機能を持つ新しいcommitを指し");
        assertThat(stageTwo).contains("未公開の通知機能commitがfeature/profileに置かれ、正しいfeature/notificationにはまだありませんでした。",
                "feature/notificationが通知機能を持つ新しいcommitを指し", "通知機能を先にcherry-pickしてからprofileを戻すことで",
                "通知機能を移す前にfeature/profileをresetすると", "通知機能を正しいbranchへ移し", "feature/profileが元のC0へ戻り",
                "二つの最終位置と、その判断の根拠は君から説明してみよう", "branch位置の安全性をQA担当へ説明する役割", "復旧根拠を確認する", "根拠と解説を開く");
        assertThat(stageTwo).doesNotContain("公開済みのmainに、必要な設定を削除する誤commitが含まれていました。", "mainには誤commitが履歴として残り");
        assertThat(stageThree).contains("main上の未commitな検索機能の変更が、正しいfeature/searchではなく作業ツリーに残っていました。",
                "mainとfeature/searchのcommit位置を変えずに", "stashで作業中の変更を一時退避してからbranchを切り替えると",
                "変更を残したままbranchを切り替えようとすると", "検索機能の作業を失わず正しいbranchへ移せたと判断するには",
                "feature/search上で検索機能の変更だけが未commitで残り", "急いで確定せず、状態を整理してから運べたね", "次の作業段取りを任されるようになった");
        assertThat(stageThree).doesNotContain("公開済みのmainに、必要な設定を削除する誤commitが含まれていました。", "未公開の通知機能commitがfeature/profileに置かれ");
        assertThat(stageFour).contains("mainとfeature/profile-messageが、同じプロフィール説明文を異なる目的で変更していました。",
                "security settingsとpublic profileの両方", "片方を選ぶのではなく要件を統合し", "oursまたはtheirsだけを採用すると",
                "双方の意図を残して統合できたと判断するには", "merge commitがmainとfeatureの両方を直接parentに持ち",
                "二つのチームへの解消説明は君に任せる", "その判断を両チームへ説明する役割を任された");
    }

    @Test void selfCheckIsDisplayOnlyAndDoesNotRenderBeforeClear() {
        String cleared = render("STAGE-GIT-01", true);
        String active = render("STAGE-GIT-01", false);
        String result = cleared.substring(cleared.indexOf("<section class=\"result\""));
        String selfCheck = result.substring(result.indexOf("<section class=\"self-check\""), result.indexOf("<section class=\"reflection\""));

        assertThat(selfCheck).contains("<details>", "復旧根拠を確認する").doesNotContain("<form", "method=\"post\"");
        assertThat(active).doesNotContain("復旧根拠を確認する", "何が壊れていたか", "修復後どうなったか", "なぜこの方法が安全か", "避けるべき選択",
                "公開済みのmainに、必要な設定を削除する誤commitが含まれていました。", "mainには誤commitが履歴として残り");
    }

    @Test void clearResponseLeadsWithAccessibleSuccessAndKeepsOnlyResetAction() {
        String cleared = render("STAGE-GIT-04", true, 2);
        String active = render("STAGE-GIT-04", false, 2);

        assertThat(cleared.indexOf("id=\"stage-header\"")).isLessThan(cleared.indexOf("id=\"clear-heading\""));
        assertThat(cleared).contains("role=\"status\"", "aria-live=\"polite\"", "aria-labelledby=\"clear-heading\"",
                "id=\"clear-heading\" tabindex=\"-1\" autofocus", "action=\"/stages/STAGE-GIT-04/reset#stage-workspace\"")
                .doesNotContain("action=\"/stages/STAGE-GIT-04/commands\"", "action=\"/stages/STAGE-GIT-04/hint\"",
                        "action=\"/stages/STAGE-GIT-04/editor\"", "name=\"command\"", "name=\"content\"", "<h2>ヒント</h2>");
        assertThat(active).contains("action=\"/stages/STAGE-GIT-04/commands#stage-workspace\"", "action=\"/stages/STAGE-GIT-04/hint#stage-sidebar-hint\"",
                "action=\"/stages/STAGE-GIT-04/editor#stage-editor\"", "action=\"/stages/STAGE-GIT-04/reset#stage-workspace\"")
                .doesNotContain("id=\"clear-heading\"", "aria-labelledby=\"clear-heading\"");
    }

    @Test void stageTwoObjectiveExplainsBothBranchPositionsAndFinalCheckout() {
        String stageTwo = render("STAGE-GIT-02", false);

        assertThat(stageTwo).contains("通知機能の変更をfeature/notificationへ移し、feature/profileを変更前のC0へ戻す。最後にfeature/notificationをcheckoutした状態にする。")
                .doesNotContain("C1をnotificationへ移し、profileをC0へ戻してnotificationにいる。");
    }

    @Test void rendersStageSpecificProgressiveIntroductionDialoguesWithoutSolutionSyntax() {
        String stageOne = render("STAGE-GIT-01", false);
        String stageTwo = render("STAGE-GIT-02", false);
        String stageThree = render("STAGE-GIT-03", false);
        String stageFour = render("STAGE-GIT-04", false);
        String stageFive = render("STAGE-GIT-05", false);

        assertThat(stageOne).contains("script-src 'self'", "src=\"/stage-dialogue.js\"", "data-dialogue-scene",
                "data-stage-key=\"STAGE-GIT-01\"", "data-dialogue-next", "data-dialogue-skip", "data-dialogue-replay",
                "href=\"#mission-heading\"", "公開済みの設定に問題が見つかり", "共有履歴を守れる復旧方針を考えます");
        assertThat(stageTwo).contains("二つの作業場所の内容と位置が食い違っていて", "私からQAへ説明します")
                .doesNotContain("公開済みの設定に問題が見つかり");
        assertThat(stageThree).contains("切り替えを忘れていました", "作業を保ったまま移す方法を考えます")
                .doesNotContain("通知機能のレビューを始めたいのですが");
        String stageFourDialogue = stageFour.substring(stageFour.indexOf("<section class=\"dialogue-scene\""), stageFour.indexOf("<main class=\"monitor-ui\""));
        assertThat(stageFourDialogue).contains("運用チーム担当", "セキュリティ設定の案内", "機能チーム担当", "公開プロフィールの案内")
                .doesNotContain("security settingsの案内", "public profileの案内");
        assertThat(stageFive).contains("似た内容を作り直すだけでは検証を再開できません", "変えていない場所まで、私が説明します")
                .doesNotContain("セキュリティ設定の案内");

        for (String html : List.of(stageOne, stageTwo, stageThree, stageFour, stageFive)) {
            String dialogue = html.substring(html.indexOf("<section class=\"dialogue-scene\""), html.indexOf("<main class=\"monitor-ui\""));
            assertThat(dialogue).doesNotContain("git status", "git switch", "git reset", "git revert", "git cherry-pick", "git reflog",
                    "git merge", "git commit", "12桁", "40桁", "C0", "C1");
        }
    }

    @Test void clearResponseShowsOneExternalReactionSeriesWithoutTheNarrativeClearScene() {
        String stageOne = render("STAGE-GIT-01", true);
        String stageFive = render("STAGE-GIT-05", true);

        assertThat(stageOne).contains("class=\"dialogue-scene clear-dialogue\"", "現場からの反応",
                "これで次のリリース確認へ戻れます", "共有履歴を守る判断ができたね")
                .doesNotContain("data-dialogue-scene", "data-dialogue-replay", "運用担当が設定の復旧確認を再開すると");
        assertThat(stageFive).contains("元の成果物だと確認できました", "消えたと決めつけなくてよかった", "次のインシデント説明は君に任せる")
                .doesNotContain("主人公が復旧根拠を運用担当へ説明すると");
    }

    @Test void rendersTheSimplifiedActiveLayoutAndStablePartialUpdateContract() {
        String stageOne = render("STAGE-GIT-01", false);
        String stageTwo = render("STAGE-GIT-02", false);
        String stageThree = render("STAGE-GIT-03", false);
        String stageFour = render("STAGE-GIT-04", false);
        String stageFive = render("STAGE-GIT-05", false);

        for (String html : List.of(stageOne, stageTwo, stageThree, stageFour, stageFive)) {
            assertThat(html).contains("src=\"/stage-partial-update.js\"", "data-stage-key=\"STAGE-GIT-",
                    "id=\"stage-header\"", "id=\"stage-sidebar-state\"", "id=\"stage-repository\"",
                    "id=\"stage-workspace\"", "id=\"stage-clear-dialogue\"", "href=\"/commands\"",
                    "現在のリポジトリ状況", "Gitコマンドを入力", "実行結果")
                    .doesNotContain("learning-card", "EVIDENCE CHECK", "name=\"learningDecision\"", "調査・対応の観点", "concept-chip");
            for (String region : List.of("stage-header", "stage-sidebar-state", "stage-repository", "stage-workspace", "stage-clear-dialogue")) {
                assertThat(html).containsOnlyOnce("id=\"" + region + "\"");
            }
        }
        assertThat(stageOne).contains("ヒントを見る <span>0</span>/4", "id=\"stage-sidebar-hint\"", "hidden=\"hidden\"");
        assertThat(stageFour).contains("限定エディタ");
    }

    @Test void doesNotRenderTheRemovedLearningAndReportCards() {
        String cleared = render("STAGE-GIT-05", true);
        String active = render("STAGE-GIT-05", false);

        assertThat(cleared).doesNotContain("learning-card", "復旧結果を報告する", "name=\"learningReport\"");
        assertThat(active).doesNotContain("learning-card", "復旧結果を報告する", "name=\"learningReport\"");
    }

    private String render(String stageKey, boolean cleared) {
        return render(stageKey, cleared, 0);
    }

    private String render(String stageKey, boolean cleared, int hintLevel) {
        StageService stages = mock(StageService.class);
        StageDefinition definition = new StageRules().definition(stageKey);
        RepositorySnapshot snapshot = "STAGE-GIT-04".equals(stageKey) && !cleared ? conflictedSnapshot() : null;
        StageView view = new StageView("request", "output", null, snapshot, hintLevel, 0, 0, 0, cleared, cleared ? 3 : 0,
                cleared ? "復旧しました。" : "未復旧", hintLevel > 0 ? List.of("段階ヒント") : List.of());
        when(stages.open(stageKey)).thenReturn(view);
        when(stages.definition(stageKey)).thenReturn(definition);
        if ("STAGE-GIT-04".equals(stageKey) && !cleared) {
            when(stages.editor(stageKey)).thenReturn(new StageEditorView("content", "a".repeat(64), "write-request"));
        }
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new StageController(stages)).setViewResolvers(viewResolver()).build();

        try {
            String rendered = mvc.perform(get("/stages/" + stageKey)
                    .requestAttr("_csrf", new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", "test-token")))
                    .andReturn().getResponse().getContentAsString();
            verify(stages).open(stageKey);
            verify(stages).definition(stageKey);
            if ("STAGE-GIT-04".equals(stageKey) && !cleared) verify(stages).editor(stageKey);
            verifyNoMoreInteractions(stages);
            return rendered;
        } catch (Exception exception) {
            throw new AssertionError("stage result template did not render", exception);
        }
    }

    private RepositorySnapshot conflictedSnapshot() {
        String c0 = "0".repeat(40), c1 = "1".repeat(40), c2 = "2".repeat(40), tree = "3".repeat(40);
        return new RepositorySnapshot(c1, tree, tree, List.of(c0), false, false, List.of(c1, c0), "main", "", "", false, true,
                false, RepositorySnapshot.StageThreeState.empty(),
                new RepositorySnapshot.StageFourState(c1, c0, c2, c0, tree, tree, "4".repeat(40),
                        List.of("src/main/resources/messages.properties"), List.of(),
                        List.of("src/main/resources/messages.properties"), List.of()));
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
