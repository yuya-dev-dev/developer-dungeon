package jp.yuya.dev.developerdungeon.contract;

public record WriteFileResponse(boolean written, String versionToken, RepositorySnapshot snapshot) { }
