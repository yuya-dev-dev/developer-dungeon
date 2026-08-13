package jp.yuya.dev.developerdungeon.app.javalearning;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.util.List;
import java.util.Optional;
import jp.yuya.dev.developerdungeon.app.javalearning.application.JavaLearningService;
import jp.yuya.dev.developerdungeon.app.javalearning.domain.JavaDifficulty;
import jp.yuya.dev.developerdungeon.app.javalearning.domain.JavaProblem;
import jp.yuya.dev.developerdungeon.app.javalearning.domain.JavaProgressStatus;
import jp.yuya.dev.developerdungeon.app.javalearning.web.JavaLearningController;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class JavaLearningControllerTest {
    @Test
    void listExposesAllThreeDifficultyGroups() throws Exception {
        JavaLearningService learning = mock(JavaLearningService.class);
        when(learning.list(JavaDifficulty.BEGINNER)).thenReturn(List.of());
        when(learning.list(JavaDifficulty.INTERMEDIATE)).thenReturn(List.of());
        when(learning.list(JavaDifficulty.ADVANCED)).thenReturn(List.of());
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new JavaLearningController(learning)).build();

        mvc.perform(get("/java/problems"))
                .andExpect(status().isOk()).andExpect(view().name("java-problems"))
                .andExpect(model().attributeExists("problemGroups"));
    }

    @Test
    void detailAndProgressUseOnlyTheCatalogSlug() throws Exception {
        JavaLearningService learning = mock(JavaLearningService.class);
        JavaProblem problem = problem();
        when(learning.find("library-beginner")).thenReturn(Optional.of(
                new JavaLearningService.ProblemDetail(problem, JavaProgressStatus.NOT_STARTED)));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new JavaLearningController(learning)).build();

        mvc.perform(get("/java/problems/library-beginner"))
                .andExpect(status().isOk()).andExpect(view().name("java-problem"))
                .andExpect(model().attributeExists("detail", "statuses"));
        mvc.perform(post("/java/problems/library-beginner/progress").param("status", "IN_PROGRESS"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/java/problems/library-beginner"));

        verify(learning).update("library-beginner", JavaProgressStatus.IN_PROGRESS);
    }

    @Test
    void unknownProblemReturnsNotFound() throws Exception {
        JavaLearningService learning = mock(JavaLearningService.class);
        when(learning.find("unknown")).thenReturn(Optional.empty());
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new JavaLearningController(learning)).build();
        mvc.perform(get("/java/problems/unknown")).andExpect(status().isNotFound());
    }

    private static JavaProblem problem() {
        return new JavaProblem("JAVA-LIBRARY-BEGINNER", "library-beginner", "図書館貸出", JavaDifficulty.BEGINNER,
                1, "本の貸出", "summary", List.of("objective"), List.of("prerequisite"), List.of("requirement"),
                List.of("constraint"), List.of("mandatory"), List.of("optional"), List.of("point"), List.of("hint"),
                new JavaProblem.MainScenario(List.of("instance"), List.of("step"), List.of("expected"), List.of("invariant")),
                new JavaProblem.BeginnerScaffold(0, List.of()), List.of("LibraryService.java"), List.of());
    }
}
