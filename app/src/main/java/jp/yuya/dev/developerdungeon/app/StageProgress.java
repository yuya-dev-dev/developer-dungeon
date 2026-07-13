package jp.yuya.dev.developerdungeon.app;

public record StageProgress(String stageKey, String title, String summary, int highestStars) {
    public boolean isCleared() { return highestStars > 0; }
}
