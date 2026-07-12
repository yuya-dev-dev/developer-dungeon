package jp.yuya.dev.developerdungeon.contract;

public record CommandResponse(int exitCode, String stdout, String stderr, boolean outputTruncated, long durationMillis, RepositorySnapshot snapshot) { }
