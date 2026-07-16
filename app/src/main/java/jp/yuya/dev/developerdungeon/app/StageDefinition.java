package jp.yuya.dev.developerdungeon.app;

import java.util.Objects;

public record StageDefinition(String key, String chapter, String title, String summary, String introduction,
                              String ticket, String objective, String allowedCommands, StageOutcome outcome,
                              StagePresentationPolicy presentation, StageLearningCard learningCard) {
    public StageDefinition {
        outcome = Objects.requireNonNull(outcome, "stage outcome");
        presentation = Objects.requireNonNull(presentation, "stage presentation");
        learningCard = Objects.requireNonNull(learningCard, "stage learning card");
    }
    public StageDefinition(String key, String chapter, String title, String summary, String introduction,
                           String ticket, String objective, String allowedCommands, StageOutcome outcome,
                           StagePresentationPolicy presentation) {
        this(key, chapter, title, summary, introduction, ticket, objective, allowedCommands, outcome, presentation,
                StageLearningCard.empty());
    }
    public StageDefinition(String key, String chapter, String title, String summary, String introduction,
                           String ticket, String objective, String allowedCommands, StageOutcome outcome) {
        this(key, chapter, title, summary, introduction, ticket, objective, allowedCommands, outcome,
                StagePresentationPolicy.fullSyntaxBasic(), StageLearningCard.empty());
    }
}
