package jp.yuya.dev.developerdungeon.contract;

public record GitCommand(CommandKind kind, String objectId, String branchName) {
    public GitCommand(CommandKind kind) { this(kind, null, null); }
    public GitCommand(CommandKind kind, String objectId) { this(kind, objectId, null); }
    public static GitCommand switchTo(String branchName) { return new GitCommand(CommandKind.SWITCH, null, branchName); }
}
