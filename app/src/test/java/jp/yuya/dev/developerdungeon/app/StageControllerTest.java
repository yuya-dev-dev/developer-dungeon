package jp.yuya.dev.developerdungeon.app;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class StageControllerTest {
    @Test void indexReadsProgressWithoutOpeningTheStage() throws Exception {
        StageOneService stage = mock(StageOneService.class);
        var progress = new StageOneService.StageProgress("STAGE-GIT-01", "公開済み変更を取り消す", "summary", 3);
        when(stage.progress()).thenReturn(progress);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new StageController(stage)).build();

        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("stages"))
                .andExpect(model().attribute("stage", progress));

        verify(stage).progress();
        verify(stage, never()).open();
        verifyNoMoreInteractions(stage);
    }
    @Test void stageRouteOpensTheExistingPlayScreen() throws Exception {
        StageOneService stage = mock(StageOneService.class);
        var view = new StageOneService.StageView("request", "output", null, null, 0, 0, 0, 0, false, 0, "未復旧");
        when(stage.open()).thenReturn(view);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new StageController(stage)).build();

        mvc.perform(get("/stages/STAGE-GIT-01"))
                .andExpect(status().isOk())
                .andExpect(view().name("stage"))
                .andExpect(model().attribute("view", view));

        verify(stage).open();
        verify(stage, never()).progress();
        verifyNoMoreInteractions(stage);
    }
    @Test void unsupportedStageDoesNotInvokeTheService() throws Exception {
        StageOneService stage = mock(StageOneService.class);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new StageController(stage)).build();

        mvc.perform(get("/stages/STAGE-GIT-02"))
                .andExpect(status().isNotFound());

        verifyNoInteractions(stage);
    }
}
