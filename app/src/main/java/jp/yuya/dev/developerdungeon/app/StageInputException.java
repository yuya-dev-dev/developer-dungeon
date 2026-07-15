package jp.yuya.dev.developerdungeon.app;

final class StageInputException extends IllegalArgumentException {
    private final String reasonCode;

    StageInputException(String message, String reasonCode) {
        super(message);
        this.reasonCode = reasonCode;
    }

    String reasonCode() {
        return reasonCode;
    }
}
