package jp.yuya.dev.developerdungeon.app;

import java.util.Objects;

public record StageDefinition(String key, String chapter, String title, String summary, String introduction,
                              String ticket, String objective, String allowedCommands, StageOutcome outcome) {
    public StageDefinition { outcome = Objects.requireNonNull(outcome, "stage outcome"); }
}
