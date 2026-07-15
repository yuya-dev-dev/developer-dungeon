package jp.yuya.dev.developerdungeon.app;

import java.util.List;
import java.util.Objects;

public record StagePresentationPolicy(GuidanceMode guidanceMode, IncidentBoardMode incidentBoardMode,
                                      List<String> conceptCategories) {
    public StagePresentationPolicy {
        guidanceMode = Objects.requireNonNull(guidanceMode, "guidance mode");
        incidentBoardMode = Objects.requireNonNull(incidentBoardMode, "incident board mode");
        conceptCategories = List.copyOf(conceptCategories);
    }
    public static StagePresentationPolicy fullSyntaxBasic() {
        return new StagePresentationPolicy(GuidanceMode.FULL_SYNTAX, IncidentBoardMode.BASIC, List.of());
    }
    public static StagePresentationPolicy conceptOnlyBasic(String... categories) {
        return new StagePresentationPolicy(GuidanceMode.CONCEPT_ONLY, IncidentBoardMode.BASIC, List.of(categories));
    }
    public static StagePresentationPolicy conceptOnlyOff(String... categories) {
        return new StagePresentationPolicy(GuidanceMode.CONCEPT_ONLY, IncidentBoardMode.OFF, List.of(categories));
    }
    public static StagePresentationPolicy conceptOnlyRedactedBranches(String... categories) {
        return new StagePresentationPolicy(GuidanceMode.CONCEPT_ONLY, IncidentBoardMode.REDACTED_BRANCHES, List.of(categories));
    }
    public enum GuidanceMode { FULL_SYNTAX, CONCEPT_ONLY }
    public enum IncidentBoardMode { OFF, BASIC, REDACTED_BRANCHES }
}
