package jp.yuya.dev.developerdungeon.app.javalearning.domain;

import java.util.List;

public record JavaProblem(
        String key,
        String slug,
        String theme,
        JavaDifficulty difficulty,
        int order,
        String title,
        String summary,
        List<String> learningObjectives,
        List<String> prerequisites,
        List<String> requirements,
        List<String> constraints,
        List<String> mandatoryRequirements,
        List<String> optionalRequirements,
        List<String> designPoints,
        List<String> hints,
        MainScenario mainScenario,
        BeginnerScaffold beginnerScaffold,
        List<String> referenceFiles,
        List<ReferenceSource> referenceSources) {

    public JavaProblem withReferenceSources(List<ReferenceSource> sources) {
        return new JavaProblem(key, slug, theme, difficulty, order, title, summary, learningObjectives,
                prerequisites, requirements, constraints, mandatoryRequirements, optionalRequirements,
                designPoints, hints, mainScenario, beginnerScaffold, referenceFiles, List.copyOf(sources));
    }

    public record MainScenario(
            List<String> instances,
            List<String> steps,
            List<String> expectedResults,
            List<String> invariants) { }

    public record BeginnerScaffold(int classCount, List<ClassSpecification> classes) { }

    public record ClassSpecification(
            String name,
            String purpose,
            int constructorCount,
            List<String> constructors,
            int fieldCount,
            int methodCount,
            List<String> fields,
            List<String> methods) { }

    public record ReferenceSource(String fileName, String source) { }
}
