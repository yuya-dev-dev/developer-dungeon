package jp.yuya.dev.developerdungeon.app.javalearning.content;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import jp.yuya.dev.developerdungeon.app.javalearning.domain.JavaDifficulty;
import jp.yuya.dev.developerdungeon.app.javalearning.domain.JavaProblem;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class JavaProblemCatalog {
    private static final String ROOT = "java-problems/";
    private static final Pattern SAFE_SEGMENT = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");
    private static final Pattern SAFE_JAVA_FILE = Pattern.compile("[A-Z][A-Za-z0-9]*\\.java");
    private static final int MAX_REFERENCE_BYTES = 64 * 1024;
    private static final int MAX_PROBLEM_REFERENCE_BYTES = 256 * 1024;
    private final List<JavaProblem> problems;
    private final Map<String, JavaProblem> bySlug;

    public JavaProblemCatalog(ObjectMapper objectMapper) {
        try {
            CatalogManifest manifest = readJson(objectMapper, ROOT + "catalog.json", CatalogManifest.class);
            List<JavaProblem> loaded = new ArrayList<>();
            for (String directory : manifest.directories()) {
                require(SAFE_SEGMENT.matcher(directory).matches(), "invalid catalog directory: " + directory);
                JavaProblem problem = readJson(objectMapper, ROOT + directory + "/problem.json", JavaProblem.class);
                require(directory.equals(problem.slug()), "catalog directory and slug differ: " + directory);
                List<JavaProblem.ReferenceSource> sources = new ArrayList<>();
                int problemReferenceBytes = 0;
                for (String fileName : problem.referenceFiles()) {
                    require(SAFE_JAVA_FILE.matcher(fileName).matches(), "invalid reference file: " + fileName);
                    String path = ROOT + directory + "/reference/" + fileName;
                    byte[] bytes = readBytes(path);
                    require(bytes.length <= MAX_REFERENCE_BYTES, "reference file is too large: " + fileName);
                    problemReferenceBytes = Math.addExact(problemReferenceBytes, bytes.length);
                    String source = new String(bytes, StandardCharsets.UTF_8);
                    String expectedPackage = "jp.yuya.dev.developerdungeon.javaproblems." + directory.replace('-', '.');
                    require(Pattern.compile("(?m)^package\\s+" + Pattern.quote(expectedPackage) + ";\\s*$")
                            .matcher(source).find(), "reference package differs: " + fileName);
                    String typeName = fileName.substring(0, fileName.length() - 5);
                    require(Pattern.compile("(?m)^public\\s+(?:final\\s+)?(?:class|record|interface|enum)\\s+"
                                    + Pattern.quote(typeName) + "\\b")
                            .matcher(source).find(),
                            "public type and file name differ: " + fileName);
                    sources.add(new JavaProblem.ReferenceSource(fileName, source));
                }
                require(problemReferenceBytes <= MAX_PROBLEM_REFERENCE_BYTES,
                        "reference files are too large in total: " + directory);
                loaded.add(problem.withReferenceSources(sources));
            }
            this.problems = validate(loaded);
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

    private static List<JavaProblem> validate(List<JavaProblem> loaded) {
        require(loaded.size() == 9, "Java MVP must contain exactly nine problems");
        Set<String> keys = new HashSet<>();
        Set<String> slugs = new HashSet<>();
        Set<Integer> orders = new HashSet<>();
        Map<JavaDifficulty, Map<String, Integer>> matrix = new EnumMap<>(JavaDifficulty.class);
        for (JavaProblem problem : loaded) {
            require(notBlank(problem.key()) && keys.add(problem.key()), "problem key is blank or duplicated");
            require(notBlank(problem.slug()) && SAFE_SEGMENT.matcher(problem.slug()).matches() && slugs.add(problem.slug()),
                    "problem slug is blank, unsafe, or duplicated");
            require(problem.order() >= 1 && problem.order() <= 9 && orders.add(problem.order()), "problem order is invalid or duplicated");
            require(notBlank(problem.theme()) && notBlank(problem.title()) && notBlank(problem.summary()), "problem summary fields are blank");
            require(nonEmpty(problem.learningObjectives()) && nonEmpty(problem.requirements())
                    && nonEmpty(problem.mandatoryRequirements()) && nonEmpty(problem.designPoints()), "required problem sections are empty");
            require(nonEmpty(problem.referenceFiles()) && problem.referenceFiles().size() == problem.referenceSources().size(),
                    "reference files are missing");
            if (problem.difficulty() == JavaDifficulty.BEGINNER) {
                require(problem.beginnerScaffold() != null && nonEmpty(problem.beginnerScaffold().classes()),
                        "beginner scaffold is missing");
                require(problem.beginnerScaffold().classCount() == problem.beginnerScaffold().classes().size(),
                        "beginner class count differs");
                for (JavaProblem.ClassSpecification specification : problem.beginnerScaffold().classes()) {
                    require(specification.constructorCount() == specification.constructors().size(),
                            "beginner constructor count differs");
                    require(specification.fieldCount() == specification.fields().size(), "beginner field count differs");
                    require(specification.methodCount() == specification.methods().size(), "beginner method count differs");
                }
            } else {
                require(problem.beginnerScaffold() == null, "scaffold is only allowed for beginner problems");
            }
            matrix.computeIfAbsent(problem.difficulty(), ignored -> new HashMap<>()).merge(problem.theme(), 1, Integer::sum);
        }
        require(orders.equals(Set.of(1, 2, 3, 4, 5, 6, 7, 8, 9)), "problem orders must be continuous");
        for (JavaDifficulty difficulty : JavaDifficulty.values()) {
            require(matrix.containsKey(difficulty) && matrix.get(difficulty).size() == 3
                    && matrix.get(difficulty).values().stream().allMatch(count -> count == 1),
                    "each difficulty must contain one problem for all three themes");
        }
        return loaded.stream().sorted(java.util.Comparator.comparingInt(JavaProblem::order)).toList();
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static boolean nonEmpty(List<?> values) {
        return values != null && !values.isEmpty();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private static <T> T readJson(ObjectMapper mapper, String path, Class<T> type) throws IOException {
        try (InputStream input = new ClassPathResource(path).getInputStream()) {
            return mapper.readValue(input, type);
        }
    }

    private static byte[] readBytes(String path) throws IOException {
        try (InputStream input = new ClassPathResource(path).getInputStream()) {
            return input.readAllBytes();
        }
    }

    private record CatalogManifest(List<String> directories) { }
}
