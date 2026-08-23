package jp.yuya.dev.developerdungeon.app.javalearning.content;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import jp.yuya.dev.developerdungeon.app.javalearning.domain.JavaProblem;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.ObjectMapper;

final class JavaProblemContentLoader {
    private static final String ROOT = "java-problems/";
    private final ObjectMapper objectMapper;

    JavaProblemContentLoader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    List<String> loadDirectories() throws IOException {
        return readJson(ROOT + "catalog.json", CatalogManifest.class).directories();
    }

    JavaProblem loadProblem(String directory) throws IOException {
        return readJson(ROOT + directory + "/problem.json", JavaProblem.class);
    }

    LoadedReference loadReference(String directory, String fileName) throws IOException {
        String path = ROOT + directory + "/reference/" + fileName;
        try (InputStream input = new ClassPathResource(path).getInputStream()) {
            byte[] bytes = input.readAllBytes();
            return new LoadedReference(new String(bytes, StandardCharsets.UTF_8), bytes.length);
        }
    }

    private <T> T readJson(String path, Class<T> type) throws IOException {
        try (InputStream input = new ClassPathResource(path).getInputStream()) {
            return objectMapper.readValue(input, type);
        }
    }

    record LoadedReference(String source, int byteCount) { }

    private record CatalogManifest(List<String> directories) { }
}
