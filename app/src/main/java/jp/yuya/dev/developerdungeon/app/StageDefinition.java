package jp.yuya.dev.developerdungeon.app;

import java.util.Objects;

public record StageDefinition(String key, String chapter, String title, String summary, String introduction,
                              String ticket, String objective, String allowedCommands, StageOutcome outcome,
                              StagePresentationPolicy presentation) {
    public StageDefinition {
        outcome = Objects.requireNonNull(outcome, "stage outcome");
        presentation = Objects.requireNonNull(presentation, "stage presentation");
    }
    public StageDefinition(String key, String chapter, String title, String summary, String introduction,
                           String ticket, String objective, String allowedCommands, StageOutcome outcome) {
        this(key, chapter, title, summary, introduction, ticket, objective, allowedCommands, outcome,
                StagePresentationPolicy.fullSyntaxBasic());
    }
}
