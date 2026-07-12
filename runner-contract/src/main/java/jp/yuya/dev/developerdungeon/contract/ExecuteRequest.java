package jp.yuya.dev.developerdungeon.contract;

public record ExecuteRequest(String attemptId, String requestId, String workspaceId, long generation, GitCommand command) { }
