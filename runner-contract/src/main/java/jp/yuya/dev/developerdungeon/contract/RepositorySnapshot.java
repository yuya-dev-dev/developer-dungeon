package jp.yuya.dev.developerdungeon.contract;

import java.util.List;

public record RepositorySnapshot(
        String headObjectId,
        String headTreeId,
        String firstParentTreeId,
        List<String> headParents,
        boolean clean,
        boolean revertInProgress,
        List<String> ancestorObjectIds,
        String currentBranch,
        String featureProfileTip,
        String featureNotificationTip,
        boolean cherryPickInProgress,
        boolean mergeInProgress,
        boolean rebaseInProgress,
        StageThreeState stageThree,
        StageFourState stageFour,
        StageFiveState stageFive,
        TrainingState training) {
    public RepositorySnapshot {
        headParents = List.copyOf(headParents);
        ancestorObjectIds = List.copyOf(ancestorObjectIds);
        stageThree = stageThree == null ? StageThreeState.empty() : stageThree;
        stageFour = stageFour == null ? StageFourState.empty() : stageFour;
        stageFive = stageFive == null ? StageFiveState.empty() : stageFive;
        training = training == null ? TrainingState.empty() : training;
    }
    public RepositorySnapshot(String headObjectId, String headTreeId, String firstParentTreeId,
                              List<String> headParents, boolean clean, boolean revertInProgress,
                              List<String> ancestorObjectIds, String currentBranch, String featureProfileTip,
                              String featureNotificationTip, boolean cherryPickInProgress, boolean mergeInProgress,
                              boolean rebaseInProgress, StageThreeState stageThree) {
        this(headObjectId, headTreeId, firstParentTreeId, headParents, clean, revertInProgress, ancestorObjectIds,
                currentBranch, featureProfileTip, featureNotificationTip, cherryPickInProgress, mergeInProgress,
                rebaseInProgress, stageThree, StageFourState.empty(), StageFiveState.empty(), TrainingState.empty());
    }
    public RepositorySnapshot(String headObjectId, String headTreeId, String firstParentTreeId,
                              List<String> headParents, boolean clean, boolean revertInProgress,
                              List<String> ancestorObjectIds, String currentBranch, String featureProfileTip,
                              String featureNotificationTip, boolean cherryPickInProgress, boolean mergeInProgress,
                              boolean rebaseInProgress, StageThreeState stageThree, StageFourState stageFour) {
        this(headObjectId, headTreeId, firstParentTreeId, headParents, clean, revertInProgress, ancestorObjectIds,
                currentBranch, featureProfileTip, featureNotificationTip, cherryPickInProgress, mergeInProgress,
                rebaseInProgress, stageThree, stageFour, StageFiveState.empty(), TrainingState.empty());
    }
    public RepositorySnapshot(String headObjectId, String headTreeId, String firstParentTreeId,
                              List<String> headParents, boolean clean, boolean revertInProgress,
                              List<String> ancestorObjectIds, String currentBranch, String featureProfileTip,
                              String featureNotificationTip, boolean cherryPickInProgress, boolean mergeInProgress,
                              boolean rebaseInProgress, StageThreeState stageThree, StageFourState stageFour,
                              StageFiveState stageFive) {
        this(headObjectId, headTreeId, firstParentTreeId, headParents, clean, revertInProgress, ancestorObjectIds,
                currentBranch, featureProfileTip, featureNotificationTip, cherryPickInProgress, mergeInProgress,
                rebaseInProgress, stageThree, stageFour, stageFive, TrainingState.empty());
    }
    public RepositorySnapshot(String headObjectId, String headTreeId, String firstParentTreeId,
                              List<String> headParents, boolean clean, boolean revertInProgress,
                              List<String> ancestorObjectIds, String currentBranch, String featureProfileTip,
                              String featureNotificationTip, boolean cherryPickInProgress) {
        this(headObjectId, headTreeId, firstParentTreeId, headParents, clean, revertInProgress, ancestorObjectIds,
                currentBranch, featureProfileTip, featureNotificationTip, cherryPickInProgress, false, false, StageThreeState.empty());
    }
    public RepositorySnapshot(String headObjectId, String headTreeId, String firstParentTreeId,
                              List<String> headParents, boolean clean, boolean revertInProgress,
                              List<String> ancestorObjectIds) {
        this(headObjectId, headTreeId, firstParentTreeId, headParents, clean, revertInProgress,
                ancestorObjectIds, "", "", "", false);
    }

    public record StageThreeState(String mainTip, String featureSearchTip, String featureSearchParent,
                                  String searchFileBlobId, List<String> workingTreePaths, List<String> indexPaths,
                                  List<String> unmergedPaths, List<String> untrackedPaths, List<String> stashObjectIds) {
        public StageThreeState {
            mainTip = requireId(mainTip); featureSearchTip = requireId(featureSearchTip); featureSearchParent = requireId(featureSearchParent);
            searchFileBlobId = requireId(searchFileBlobId);
            workingTreePaths = List.copyOf(workingTreePaths); indexPaths = List.copyOf(indexPaths);
            unmergedPaths = List.copyOf(unmergedPaths); untrackedPaths = List.copyOf(untrackedPaths); stashObjectIds = List.copyOf(stashObjectIds);
        }
        public static StageThreeState empty() { return new StageThreeState("", "", "", "", List.of(), List.of(), List.of(), List.of(), List.of()); }
        private static String requireId(String value) { return value == null ? "" : value; }
    }

    public record StageFourState(String mainTip, String mainParent, String featureProfileMessageTip,
                                 String featureProfileMessageParent, String mainTreeId, String featureTreeId,
                                 String messagesBlobId, List<String> workingTreePaths, List<String> indexPaths,
                                 List<String> unmergedPaths, List<String> untrackedPaths) {
        public StageFourState {
            mainTip = value(mainTip); mainParent = value(mainParent);
            featureProfileMessageTip = value(featureProfileMessageTip);
            featureProfileMessageParent = value(featureProfileMessageParent);
            mainTreeId = value(mainTreeId); featureTreeId = value(featureTreeId); messagesBlobId = value(messagesBlobId);
            workingTreePaths = List.copyOf(workingTreePaths); indexPaths = List.copyOf(indexPaths);
            unmergedPaths = List.copyOf(unmergedPaths); untrackedPaths = List.copyOf(untrackedPaths);
        }
        public static StageFourState empty() {
            return new StageFourState("", "", "", "", "", "", "", List.of(), List.of(), List.of(), List.of());
        }
        private static String value(String value) { return value == null ? "" : value; }
    }

    public record StageFiveState(String mainTip, String recoveryTargetId, String recoveryTargetParent,
                                 String recoveryTargetTreeId, String paymentRetryTip, List<String> localBranches) {
        public StageFiveState {
            mainTip = value(mainTip); recoveryTargetId = value(recoveryTargetId);
            recoveryTargetParent = value(recoveryTargetParent); recoveryTargetTreeId = value(recoveryTargetTreeId);
            localBranches = List.copyOf(localBranches == null ? List.of() : localBranches);
        }
        public static StageFiveState empty() { return new StageFiveState("", "", "", "", null, List.of()); }
        private static String value(String value) { return value == null ? "" : value; }
    }

    public record TrainingState(String mainTip, String trainingBranchTip, List<String> headPaths,
                                List<String> workingTreePaths, List<String> indexPaths,
                                List<String> untrackedPaths, List<String> ignoredPaths,
                                String introBlobId, String ignoreBlobId, String configBlobId,
                                String reportBlobId, String handoffBlobId, boolean reportExists) {
        public TrainingState {
            mainTip = value(mainTip); trainingBranchTip = nullable(trainingBranchTip);
            headPaths = copy(headPaths); workingTreePaths = copy(workingTreePaths);
            indexPaths = copy(indexPaths); untrackedPaths = copy(untrackedPaths); ignoredPaths = copy(ignoredPaths);
            introBlobId = value(introBlobId); ignoreBlobId = value(ignoreBlobId);
            configBlobId = value(configBlobId); reportBlobId = value(reportBlobId); handoffBlobId = value(handoffBlobId);
        }
        public static TrainingState empty() {
            return new TrainingState("", null, List.of(), List.of(), List.of(), List.of(), List.of(),
                    "", "", "", "", "", false);
        }
        private static List<String> copy(List<String> values) { return List.copyOf(values == null ? List.of() : values); }
        private static String nullable(String value) { return value == null || value.isBlank() ? null : value; }
        private static String value(String value) { return value == null ? "" : value; }
    }
}
