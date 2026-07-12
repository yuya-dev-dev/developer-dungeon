package jp.yuya.dev.developerdungeon.contract;

public record DestroyRequest(String attemptId, String requestId, String workspaceId, long generation, String reason) { }
