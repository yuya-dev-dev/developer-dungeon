package jp.yuya.dev.developerdungeon.contract;

import java.util.List;

public record GitCommand(CommandKind kind, List<String> objectIds) {
    public GitCommand {
        objectIds = List.copyOf(objectIds);
    }
}
