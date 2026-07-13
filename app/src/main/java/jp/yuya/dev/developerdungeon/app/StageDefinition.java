package jp.yuya.dev.developerdungeon.app;

public record StageDefinition(String key, String chapter, String title, String summary, String introduction,
                              String ticket, String objective, String allowedCommands) { }
