package jp.yuya.dev.developerdungeon.app.javalearning.persistence;

import java.util.LinkedHashMap;
import java.util.Map;
import jp.yuya.dev.developerdungeon.app.javalearning.domain.JavaProgressStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcJavaProgressRepository implements JavaProgressRepository {
    private final JdbcTemplate jdbc;

    public JdbcJavaProgressRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Map<String, JavaProgressStatus> findAll() {
        Map<String, JavaProgressStatus> result = new LinkedHashMap<>();
        jdbc.query("SELECT problem_key,status FROM java_problem_progress ORDER BY problem_key",
                        (rows, rowNumber) -> Map.entry(rows.getString("problem_key"),
                                JavaProgressStatus.valueOf(rows.getString("status"))))
                .forEach(entry -> result.put(entry.getKey(), entry.getValue()));
        return Map.copyOf(result);
    }

    @Override
    public void save(String problemKey, JavaProgressStatus status) {
        jdbc.update("""
                INSERT INTO java_problem_progress(problem_key,status,updated_at)
                VALUES (?,?,CURRENT_TIMESTAMP)
                ON CONFLICT (problem_key) DO UPDATE
                SET status=EXCLUDED.status,updated_at=CURRENT_TIMESTAMP
                """, problemKey, status.name());
    }
}
