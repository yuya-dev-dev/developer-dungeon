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
        boolean cherryPickInProgress) {
    public RepositorySnapshot {
        headParents = List.copyOf(headParents);
        ancestorObjectIds = List.copyOf(ancestorObjectIds);
    }
    public RepositorySnapshot(String headObjectId, String headTreeId, String firstParentTreeId,
                              List<String> headParents, boolean clean, boolean revertInProgress,
                              List<String> ancestorObjectIds) {
        this(headObjectId, headTreeId, firstParentTreeId, headParents, clean, revertInProgress,
                ancestorObjectIds, "", "", "", false);
    }
}
