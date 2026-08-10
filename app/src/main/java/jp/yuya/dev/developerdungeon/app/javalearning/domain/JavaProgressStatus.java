package jp.yuya.dev.developerdungeon.app.javalearning.domain;

public enum JavaProgressStatus {
    NOT_STARTED("未着手"),
    IN_PROGRESS("学習中"),
    COMPLETED("完了");

    private final String label;

    JavaProgressStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
