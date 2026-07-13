package jp.yuya.dev.developerdungeon.contract;

public record WriteFileRequest(String attemptId, String requestId, String workspaceId, long generation,
                               StageFileKey fileKey, String content, String versionToken) { }
