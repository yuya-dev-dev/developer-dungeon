package jp.yuya.dev.developerdungeon.app;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import jp.yuya.dev.developerdungeon.contract.GitCommand;
import jp.yuya.dev.developerdungeon.contract.RepositorySnapshot;
import org.springframework.stereotype.Component;

@Component
class StageRules {
    private final StageCatalog catalog = new StageCatalog();
    private final StageCommandPolicy commandPolicy = new StageCommandPolicy();
    private final StageStatePolicy statePolicy = new StageStatePolicy();

    List<StageDefinition> definitions() { return catalog.definitions(); }
    List<StageDefinition> trainingDefinitions() { return catalog.trainingDefinitions(); }
    StageDefinition definition(String stageKey) { return catalog.definition(stageKey); }
    GitCommand parse(StageDefinition definition, String raw) { return commandPolicy.parse(definition, raw); }
    StageTargets capture(StageDefinition definition, RepositorySnapshot snapshot) {
        return statePolicy.capture(definition, snapshot);
    }
    GitCommand normalize(StageDefinition definition, GitCommand command, StageTargets targets, Set<String> displayed) {
        return commandPolicy.normalize(definition, command, targets, displayed);
    }
    void recordDisplayedObjects(StageDefinition definition, GitCommand command, String output, StageTargets targets,
                                Set<String> displayed) {
        commandPolicy.recordDisplayedObjects(definition, command, output, targets, displayed);
    }
    void revealHintTargets(StageDefinition definition, int hintLevel, StageTargets targets, Set<String> displayed) {
        statePolicy.revealHintTargets(definition, hintLevel, targets, displayed);
    }
    List<String> hints(StageDefinition definition, int hintLevel, StageTargets targets) {
        return statePolicy.hints(definition, hintLevel, targets);
    }
    StageGrade grade(StageDefinition definition, RepositorySnapshot snapshot, StageTargets targets,
                     int highestHint, int playerResets) {
        return statePolicy.grade(definition, snapshot, targets, highestHint, playerResets);
    }

    record StageTargets(String primaryObjectId, String secondaryObjectId, String expectedTreeId, Set<String> allowedObjects,
                        RepositorySnapshot.TrainingState training) {
        StageTargets(String primaryObjectId, String secondaryObjectId, String expectedTreeId, Set<String> allowedObjects) {
            this(primaryObjectId, secondaryObjectId, expectedTreeId, allowedObjects, RepositorySnapshot.TrainingState.empty());
        }
        StageTargets { allowedObjects = Set.copyOf(new LinkedHashSet<>(allowedObjects)); }
    }
}
