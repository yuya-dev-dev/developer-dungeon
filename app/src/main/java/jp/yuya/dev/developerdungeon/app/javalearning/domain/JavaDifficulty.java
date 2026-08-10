package jp.yuya.dev.developerdungeon.app.javalearning.domain;

public enum JavaDifficulty {
    BEGINNER("初級"),
    INTERMEDIATE("中級"),
    ADVANCED("上級");

    private final String label;

    JavaDifficulty(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
