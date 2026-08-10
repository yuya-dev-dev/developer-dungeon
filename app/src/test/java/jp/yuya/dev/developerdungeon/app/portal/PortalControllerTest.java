package jp.yuya.dev.developerdungeon.app.portal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.view.InternalResourceView;

class PortalControllerTest {
    @Test
    void indexRendersTheTrainingProgramPortal() throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new PortalController())
                .setSingleView(new InternalResourceView("/WEB-INF/test.html")).build();

        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("title"));
    }
}
