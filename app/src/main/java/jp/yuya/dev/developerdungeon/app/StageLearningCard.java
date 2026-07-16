package jp.yuya.dev.developerdungeon.app;

import java.util.List;

/** Static, non-scoring prompts for the player's evidence and reporting reflection. */
public record StageLearningCard(String evidence, String decisionPrompt, List<String> decisionOptions,
                                String result, String reportPrompt, List<String> reportOptions) {
    public StageLearningCard {
        evidence = required(evidence);
        decisionPrompt = required(decisionPrompt);
        decisionOptions = List.copyOf(decisionOptions == null ? List.of() : decisionOptions);
        result = required(result);
        reportPrompt = required(reportPrompt);
        reportOptions = List.copyOf(reportOptions == null ? List.of() : reportOptions);
    }

    static StageLearningCard empty() {
        return new StageLearningCard("", "", List.of(), "", "", List.of());
    }

    private static String required(String value) {
        return value == null ? "" : value;
    }
}
