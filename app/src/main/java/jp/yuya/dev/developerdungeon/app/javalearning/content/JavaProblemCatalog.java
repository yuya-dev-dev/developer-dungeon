package jp.yuya.dev.developerdungeon.app.javalearning.content;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import jp.yuya.dev.developerdungeon.app.javalearning.domain.JavaProblem;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class JavaProblemCatalog {
    private final List<JavaProblem> problems;
    private final Map<String, JavaProblem> bySlug;

    public JavaProblemCatalog(ObjectMapper objectMapper) {
        JavaProblemContentLoader loader = new JavaProblemContentLoader(objectMapper);
        JavaProblemCatalogValidator validator = new JavaProblemCatalogValidator();
        try {
            List<JavaProblem> loaded = new ArrayList<>();
            for (String directory : loader.loadDirectories()) {
                validator.validateDirectory(directory);
                JavaProblem problem = loader.loadProblem(directory);
                validator.validateProblemLocation(directory, problem);
                List<JavaProblem.ReferenceSource> sources = new ArrayList<>();
                int problemReferenceBytes = 0;
                for (String fileName : problem.referenceFiles()) {
                    validator.validateReferenceFileName(fileName);
                    JavaProblemContentLoader.LoadedReference reference = loader.loadReference(directory, fileName);
                    validator.validateReferenceFileSize(fileName, reference.byteCount());
                    problemReferenceBytes = validator.addReferenceBytes(problemReferenceBytes, reference.byteCount());
                    validator.validateReferenceSource(directory, fileName, reference.source());
                    sources.add(new JavaProblem.ReferenceSource(fileName, reference.source()));
                }
                validator.validateProblemReferenceSize(directory, problemReferenceBytes);
                loaded.add(problem.withReferenceSources(sources));
            }
            this.problems = validator.validateCatalog(loaded);
            Map<String, JavaProblem> index = new LinkedHashMap<>();
            this.problems.forEach(problem -> index.put(problem.slug(), problem));
            this.bySlug = Map.copyOf(index);
        } catch (IOException exception) {
            throw new IllegalStateException("Java problem catalog could not be loaded", exception);
        }
    }

    public List<JavaProblem> all() {
        return problems;
    }

    public Optional<JavaProblem> findBySlug(String slug) {
        return Optional.ofNullable(bySlug.get(slug));
    }
}
