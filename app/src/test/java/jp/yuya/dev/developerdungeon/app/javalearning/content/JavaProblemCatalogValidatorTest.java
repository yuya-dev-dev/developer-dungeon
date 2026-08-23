package jp.yuya.dev.developerdungeon.app.javalearning.content;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.InputStream;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

class JavaProblemCatalogValidatorTest {
    private static final int KIB = 1024;
    private final JavaProblemCatalogValidator validator = new JavaProblemCatalogValidator();

    @Test
    void rejectsUnsafeResourceNamesWithTheExistingMessages() {
        assertThatThrownBy(() -> validator.validateDirectory("../secrets"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("invalid catalog directory: ../secrets")
                .hasNoCause();
        assertThatThrownBy(() -> validator.validateReferenceFileName("../Main.java"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("invalid reference file: ../Main.java")
                .hasNoCause();
        assertThatThrownBy(() -> validator.validateDirectory(null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> validator.validateReferenceFileName(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void acceptsSizeLimitsAndRejectsTheFirstByteAboveThem() {
        assertThatCode(() -> validator.validateReferenceFileSize("Main.java", 64 * KIB))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> validator.validateReferenceFileSize("Main.java", 64 * KIB + 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("reference file is too large: Main.java")
                .hasNoCause();

        assertThatCode(() -> validator.validateProblemReferenceSize("library-beginner", 256 * KIB))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> validator.validateProblemReferenceSize("library-beginner", 256 * KIB + 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("reference files are too large in total: library-beginner")
                .hasNoCause();
    }

    @Test
    void keepsReferenceSourceValidationMessages() {
        String expectedPackage = "package jp.yuya.dev.developerdungeon.javaproblems.library.beginner;\n";

        assertThatThrownBy(() -> validator.validateReferenceSource("library-beginner", "Main.java",
                        "package wrong;\npublic class Main { public static void main(String[] args) {} }"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("reference package differs: Main.java")
                .hasNoCause();
        assertThatThrownBy(() -> validator.validateReferenceSource("library-beginner", "Main.java",
                        expectedPackage + "public class Other { public static void main(String[] args) {} }"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("public type and file name differ: Main.java")
                .hasNoCause();
        assertThatThrownBy(() -> validator.validateReferenceSource("library-beginner", "Main.java",
                        expectedPackage + "public class Main {}"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Main.java must declare public static void main(String[]): library-beginner")
                .hasNoCause();
    }

    @Test
    void keepsOverflowAndContentReadFailuresDistinct() throws Exception {
        assertThatThrownBy(() -> validator.addReferenceBytes(Integer.MAX_VALUE, 1))
                .isInstanceOf(ArithmeticException.class);

        ClassLoader original = Thread.currentThread().getContextClassLoader();
        ClassLoader missingCatalog = new ClassLoader(original) {
            @Override
            public InputStream getResourceAsStream(String name) {
                return name.equals("java-problems/catalog.json") ? null : super.getResourceAsStream(name);
            }
        };
        try {
            Thread.currentThread().setContextClassLoader(missingCatalog);
            assertThatThrownBy(() -> new JavaProblemCatalog(new ObjectMapper()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Java problem catalog could not be loaded")
                    .hasCauseInstanceOf(IOException.class);
        } finally {
            Thread.currentThread().setContextClassLoader(original);
        }

        ObjectMapper mapper = mock(ObjectMapper.class);
        JacksonException readFailure = mock(JacksonException.class);
        when(mapper.readValue(any(InputStream.class), org.mockito.ArgumentMatchers.<Class<Object>>any()))
                .thenThrow(readFailure);

        assertThatThrownBy(() -> new JavaProblemCatalog(mapper))
                .isSameAs(readFailure);
    }
}
