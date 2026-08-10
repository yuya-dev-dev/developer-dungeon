package jp.yuya.dev.developerdungeon.app.javalearning.persistence;

import java.util.Map;
import jp.yuya.dev.developerdungeon.app.javalearning.domain.JavaProgressStatus;

public interface JavaProgressRepository {
    Map<String, JavaProgressStatus> findAll();
    void save(String problemKey, JavaProgressStatus status);
}
