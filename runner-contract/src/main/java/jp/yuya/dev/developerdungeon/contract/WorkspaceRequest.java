package jp.yuya.dev.developerdungeon.contract;

public record WorkspaceRequest(String attemptId, String requestId, String stageKey, long generation) { }
