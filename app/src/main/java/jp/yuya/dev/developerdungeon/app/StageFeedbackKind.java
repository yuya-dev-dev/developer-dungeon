package jp.yuya.dev.developerdungeon.app;

public enum StageFeedbackKind {
    INITIAL("initial"),
    INFO("info"),
    INPUT_REJECTED("input_rejected"),
    SUCCEEDED("succeeded"),
    GIT_ERROR("git_error"),
    EDIT_CONFLICT("edit_conflict"),
    SYSTEM_ERROR("system_error");

    private final String cssToken;

    StageFeedbackKind(String cssToken) {
        this.cssToken = cssToken;
    }

    public String cssToken() {
        return cssToken;
    }
}
