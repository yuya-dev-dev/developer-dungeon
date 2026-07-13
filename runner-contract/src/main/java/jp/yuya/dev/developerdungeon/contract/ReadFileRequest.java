package jp.yuya.dev.developerdungeon.contract;

public record ReadFileRequest(String attemptId, String requestId, String workspaceId, long generation,
                              StageFileKey fileKey) { }
