package jp.yuya.dev.developerdungeon.contract;

public record WorkspaceResponse(String workspaceId, long generation, RepositorySnapshot snapshot) { }
