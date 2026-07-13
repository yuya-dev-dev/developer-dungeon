package jp.yuya.dev.developerdungeon.app;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class StageControllerTest {
    @Test void indexReadsOnlyProgressForBothFixedStages() throws Exception {
        StageService stages = mock(StageService.class);
        var progress = List.of(new StageProgress("STAGE-GIT-01", "公開済み変更を取り消す", "summary", 3),
                new StageProgress("STAGE-GIT-02", "間違ったbranchのcommitを移す", "summary", 0));
        when(stages.progresses()).thenReturn(progress);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new StageController(stages)).build();

        mvc.perform(get("/")).andExpect(status().isOk()).andExpect(view().name("stages")).andExpect(model().attribute("stages", progress));

        verify(stages).progresses();
        verifyNoMoreInteractions(stages);
    }
    @Test void fixedStageRouteOpensOnlyItsOwnPlayScreen() throws Exception {
        StageService stages = mock(StageService.class);
        var definition = new StageDefinition("STAGE-GIT-02", "chapter", "title", "summary", "intro", "ticket", "objective", "commands", outcome());
        var view = new StageView("request", "output", null, null, 0, 0, 0, 0, false, 0, "未復旧", List.of());
        when(stages.open("STAGE-GIT-02")).thenReturn(view);
        when(stages.definition("STAGE-GIT-02")).thenReturn(definition);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new StageController(stages)).build();

        mvc.perform(get("/stages/STAGE-GIT-02")).andExpect(status().isOk()).andExpect(view().name("stage"))
                .andExpect(model().attribute("view", view)).andExpect(model().attribute("stage", definition));

        verify(stages).open("STAGE-GIT-02");
        verify(stages).definition("STAGE-GIT-02");
        verifyNoMoreInteractions(stages);
    }
    @Test void stageThreeRouteOpensOnlyItsOwnPlayScreen() throws Exception {
        StageService stages = mock(StageService.class);
        var definition = new StageDefinition("STAGE-GIT-03", "chapter", "title", "summary", "intro", "ticket", "objective", "concepts", outcome(),
                StagePresentationPolicy.conceptOnlyOff("観察"));
        var view = new StageView("request", "output", null, null, 0, 0, 0, 0, false, 0, "未復旧", List.of());
        when(stages.open("STAGE-GIT-03")).thenReturn(view);
        when(stages.definition("STAGE-GIT-03")).thenReturn(definition);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new StageController(stages)).build();

        mvc.perform(get("/stages/STAGE-GIT-03")).andExpect(status().isOk()).andExpect(view().name("stage"));

        verify(stages).open("STAGE-GIT-03");
        verify(stages).definition("STAGE-GIT-03");
        verifyNoMoreInteractions(stages);
    }
    @Test void stageTwoPostUsesOnlyTheFixedStageTwoKey() throws Exception {
        StageService stages = mock(StageService.class);
        var definition = new StageDefinition("STAGE-GIT-02", "chapter", "title", "summary", "intro", "ticket", "objective", "commands", outcome());
        var page = new StageView("request", "output", null, null, 0, 0, 0, 0, false, 0, "未復旧", List.of());
        when(stages.execute("STAGE-GIT-02", "git status", "11111111-1111-1111-1111-111111111111")).thenReturn(page);
        when(stages.definition("STAGE-GIT-02")).thenReturn(definition);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new StageController(stages)).build();

        mvc.perform(post("/stages/STAGE-GIT-02/commands").param("command", "git status").param("requestId", "11111111-1111-1111-1111-111111111111"))
                .andExpect(status().isOk()).andExpect(view().name("stage"));

        verify(stages).execute("STAGE-GIT-02", "git status", "11111111-1111-1111-1111-111111111111");
        verify(stages).definition("STAGE-GIT-02");
        verifyNoMoreInteractions(stages);
    }

    private static StageOutcome outcome() {
        return new StageOutcome("incident", "repaired", "safe", "unsafe", "prompt", "explanation", "scene", "growth");
    }
}
