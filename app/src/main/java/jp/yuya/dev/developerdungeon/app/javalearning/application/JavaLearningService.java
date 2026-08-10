package jp.yuya.dev.developerdungeon.app.javalearning.application;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import jp.yuya.dev.developerdungeon.app.javalearning.content.JavaProblemCatalog;
import jp.yuya.dev.developerdungeon.app.javalearning.domain.JavaDifficulty;
import jp.yuya.dev.developerdungeon.app.javalearning.domain.JavaProblem;
import jp.yuya.dev.developerdungeon.app.javalearning.domain.JavaProgressStatus;
import jp.yuya.dev.developerdungeon.app.javalearning.persistence.JavaProgressRepository;
import org.springframework.stereotype.Service;

@Service
public class JavaLearningService {
    private final JavaProblemCatalog catalog;
    private final JavaProgressRepository progress;

    public JavaLearningService(JavaProblemCatalog catalog, JavaProgressRepository progress) {
        this.catalog = catalog;
        this.progress = progress;
    }

    public List<ProblemSummary> list(JavaDifficulty difficulty) {
        Map<String, JavaProgressStatus> statuses = progress.findAll();
        return catalog.all().stream()
                .filter(problem -> problem.difficulty() == difficulty)
                .map(problem -> new ProblemSummary(problem,
                        statuses.getOrDefault(problem.key(), JavaProgressStatus.NOT_STARTED)))
                .toList();
    }

    public Optional<ProblemDetail> find(String slug) {
        Map<String, JavaProgressStatus> statuses = progress.findAll();
        return catalog.findBySlug(slug).map(problem -> new ProblemDetail(problem,
                statuses.getOrDefault(problem.key(), JavaProgressStatus.NOT_STARTED)));
    }

    public void update(String slug, JavaProgressStatus status) {
        JavaProblem problem = catalog.findBySlug(slug)
                .orElseThrow(() -> new IllegalArgumentException("Unknown Java problem slug"));
        progress.save(problem.key(), status);
    }

    public record ProblemSummary(JavaProblem problem, JavaProgressStatus status) { }
    public record ProblemDetail(JavaProblem problem, JavaProgressStatus status) { }
}
