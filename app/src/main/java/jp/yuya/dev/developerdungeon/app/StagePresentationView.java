package jp.yuya.dev.developerdungeon.app;

import java.util.List;
import jp.yuya.dev.developerdungeon.contract.RepositorySnapshot;

record StagePresentationView(Guidance guidance, IncidentBoard incidentBoard) {
    static StagePresentationView from(StageDefinition stage, RepositorySnapshot snapshot) {
        boolean fullSyntax = stage.presentation().guidanceMode() == StagePresentationPolicy.GuidanceMode.FULL_SYNTAX;
        Guidance guidance = new Guidance(fullSyntax, stage.presentation().conceptCategories());
        if (stage.presentation().incidentBoardMode() == StagePresentationPolicy.IncidentBoardMode.OFF || snapshot == null) {
            return new StagePresentationView(guidance, null);
        }
        return new StagePresentationView(guidance, new IncidentBoard(snapshot.currentBranch(), snapshot.headObjectId(), snapshot.clean()));
    }

    record Guidance(boolean fullSyntax, List<String> conceptCategories) {
        Guidance { conceptCategories = List.copyOf(conceptCategories); }
    }
    record IncidentBoard(String currentBranch, String headObjectId, boolean clean) { }
}
