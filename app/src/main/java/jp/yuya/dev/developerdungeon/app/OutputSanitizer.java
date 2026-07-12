package jp.yuya.dev.developerdungeon.app;

import org.springframework.stereotype.Component;

@Component
class OutputSanitizer {
    String sanitize(String raw) {
        if (raw == null) return "";
        StringBuilder safe = new StringBuilder(raw.length());
        raw.codePoints().forEach(value -> {
            if (value == '\n' || value == '\t' || !Character.isISOControl(value)) safe.appendCodePoint(value);
            else safe.append('�');
        });
        return safe.toString();
    }
}
