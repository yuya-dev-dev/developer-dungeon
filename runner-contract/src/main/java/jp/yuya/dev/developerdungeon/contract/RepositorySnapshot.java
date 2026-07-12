package jp.yuya.dev.developerdungeon.contract;

import java.util.List;

public record RepositorySnapshot(
        String headObjectId,
        String headTreeId,
        String firstParentTreeId,
        List<String> headParents,
        boolean clean,
        boolean revertInProgress,
        List<String> ancestorObjectIds) {
    public RepositorySnapshot {
        headParents = List.copyOf(headParents);
        ancestorObjectIds = List.copyOf(ancestorObjectIds);
    }
}
