package jp.yuya.dev.developerdungeon.app;

import java.util.Objects;

public record StageOutcome(String incident, String repairedState, String safeReason, String unsafeAlternative, String selfCheckPrompt,
                           String selfCheckExplanation, String clearScene, String growthBeat) {
    public StageOutcome {
        incident = required(incident);
        repairedState = required(repairedState);
        safeReason = required(safeReason);
        unsafeAlternative = required(unsafeAlternative);
        selfCheckPrompt = required(selfCheckPrompt);
        selfCheckExplanation = required(selfCheckExplanation);
        clearScene = required(clearScene);
        growthBeat = required(growthBeat);
    }

    private static String required(String value) { return Objects.requireNonNull(value, "stage outcome text"); }
}
