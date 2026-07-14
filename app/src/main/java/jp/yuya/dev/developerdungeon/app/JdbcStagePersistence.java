package jp.yuya.dev.developerdungeon.app;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

@Repository
class JdbcStagePersistence implements StagePersistence {
    private static final String COLUMNS = "id,status,version,current_generation,workspace_id,create_request_id,cleanup_request_id,pending_stars,highest_hint_level,player_reset_count,system_recovery_count,last_sequence_no,stars";
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;

    JdbcStagePersistence(JdbcTemplate jdbc, TransactionTemplate transactions) { this.jdbc = jdbc; this.transactions = transactions; }

    @Override public SavedAttempt createStarting(UUID id, String stageKey, UUID createRequestId, Instant now) {
        jdbc.update("INSERT INTO stage_attempt(id,stage_key,status,current_generation,create_request_id,cleanup_request_id,started_at) VALUES (?,?, 'STARTING',0,?,?,?)",
                id, stageKey, createRequestId, createRequestId, Timestamp.from(now));
        return required(id);
    }
    @Override public SavedAttempt workspaceCreated(UUID id, long version, UUID workspaceId) {
        updateOne("UPDATE stage_attempt SET workspace_id=?,version=version+1 WHERE id=? AND version=? AND status='STARTING'", workspaceId,id,version);
        return required(id);
    }
    @Override public SavedAttempt activate(UUID id, long version, UUID workspaceId) {
        updateOne("UPDATE stage_attempt SET status='ACTIVE',workspace_id=?,create_request_id=NULL,cleanup_request_id=NULL,version=version+1 WHERE id=? AND version=? AND status='STARTING'", workspaceId,id,version);
        return required(id);
    }
    @Override public Optional<SavedAttempt> findOpen(String stageKey) {
        return jdbc.query("SELECT " + COLUMNS + " FROM stage_attempt WHERE stage_key=? AND status NOT IN ('CLEARED','FAILED','EXPIRED','ABANDONED')", this::map, stageKey).stream().findFirst();
    }
    @Override public SavedAttempt increaseHint(UUID id, long version, int hint) {
        updateOne("UPDATE stage_attempt SET highest_hint_level=GREATEST(highest_hint_level,?),version=version+1 WHERE id=? AND version=?", hint,id,version);
        return required(id);
    }
    @Override public boolean beginCommand(UUID id, long version, UUID requestId, long sequence, long generation, String text, String kind, Instant now) {
        try {
            transactions.executeWithoutResult(status -> {
                jdbc.update("INSERT INTO command_history(attempt_id,request_id,sequence_no,workspace_generation,entered_text,command_kind,result_kind,executed_at) VALUES (?,?,?,?,?,?, 'PENDING',?)",
                        id,requestId,sequence,generation,text,kind,Timestamp.from(now));
                updateOne("UPDATE stage_attempt SET status='EXECUTING',last_sequence_no=?,version=version+1 WHERE id=? AND version=? AND status='ACTIVE'", sequence,id,version);
            });
            return true;
        } catch (DuplicateKeyException exception) { return false; }
    }
    @Override public SavedAttempt finishCommand(UUID id, long version, UUID requestId, String result, Integer exitCode, long durationMs, UUID cleanupId, Integer pendingStars) {
        transactions.executeWithoutResult(status -> {
            updateOne("UPDATE command_history SET result_kind=?,exit_code=?,duration_ms=? WHERE attempt_id=? AND request_id=? AND result_kind='PENDING'", result,exitCode,durationMs,id,requestId);
            if (pendingStars == null) {
                updateOne("UPDATE stage_attempt SET status='ACTIVE',version=version+1 WHERE id=? AND version=? AND status='EXECUTING'", id,version);
            } else {
                updateOne("UPDATE stage_attempt SET status='CLEARING',cleanup_request_id=?,pending_stars=?,version=version+1 WHERE id=? AND version=? AND status='EXECUTING'", cleanupId,pendingStars,id,version);
            }
        });
        return required(id);
    }
    @Override public SavedAttempt recordRejected(UUID id, long version, UUID requestId, long sequence, long generation, String reason, Instant now) {
        try {
            transactions.executeWithoutResult(status -> {
                jdbc.update("INSERT INTO command_history(attempt_id,request_id,sequence_no,workspace_generation,entered_text,command_kind,result_kind,reason_code,executed_at) VALUES (?,?,?,?,NULL,NULL,'REJECTED',?,?)",
                        id,requestId,sequence,generation,reason,Timestamp.from(now));
                updateOne("UPDATE stage_attempt SET last_sequence_no=?,version=version+1 WHERE id=? AND version=? AND status='ACTIVE'",sequence,id,version);
            });
        } catch (DuplicateKeyException ignored) { return required(id); }
        return required(id);
    }
    @Override public SavedAttempt prepareSystemRecovery(UUID id, long version, UUID cleanupId, UUID createId) {
        return transactions.execute(status -> {
            SavedAttempt current = required(id);
            if (current.status().equals("EXECUTING")) {
                jdbc.update("UPDATE command_history SET result_kind='RUNNER_ERROR',duration_ms=0 WHERE attempt_id=? AND result_kind='PENDING'",id);
            }
            if (current.status().equals("ACTIVE") || current.status().equals("EXECUTING")) {
                updateOne("UPDATE stage_attempt SET status='RESETTING',cleanup_request_id=?,create_request_id=?,version=version+1 WHERE id=? AND version=?",cleanupId,createId,id,version);
            }
            return required(id);
        });
    }
    @Override public SavedAttempt beginReset(UUID id, long version, UUID cleanupId, UUID createId) {
        updateOne("UPDATE stage_attempt SET status='RESETTING',cleanup_request_id=?,create_request_id=?,version=version+1 WHERE id=? AND version=? AND status IN ('ACTIVE','EXECUTING')", cleanupId,createId,id,version);
        return required(id);
    }
    @Override public SavedAttempt beginCreateCleanup(UUID id, long version, UUID workspaceId) {
        updateOne("UPDATE stage_attempt SET status='CLEANUP_PENDING',workspace_id=?,version=version+1 WHERE id=? AND version=? AND status='STARTING'", workspaceId,id,version);
        return required(id);
    }
    @Override public SavedAttempt markCleanupPending(UUID id, long version) {
        updateOne("UPDATE stage_attempt SET status='CLEANUP_PENDING',version=version+1 WHERE id=? AND version=? AND status IN ('STARTING','RESETTING','CLEARING')", id,version);
        return required(id);
    }
    @Override public SavedAttempt restartStartingAfterCleanup(UUID id, long version) {
        updateOne("UPDATE stage_attempt SET status='STARTING',workspace_id=NULL,cleanup_request_id=create_request_id,version=version+1 WHERE id=? AND version=? AND status='CLEANUP_PENDING'", id,version);
        return required(id);
    }
    @Override public SavedAttempt completeReset(UUID id, long version, boolean system, UUID historyRequestId, Instant now) {
        return transactions.execute(status -> {
            SavedAttempt before = required(id);
            long sequence = before.lastSequence() + 1;
            jdbc.update("INSERT INTO command_history(attempt_id,request_id,sequence_no,workspace_generation,result_kind,executed_at) VALUES (?,?,?,?, 'RESET',?)",
                    id,historyRequestId,sequence,before.generation(),Timestamp.from(now));
            String counter = system ? "system_recovery_count" : "player_reset_count";
            updateOne("UPDATE stage_attempt SET status='STARTING',workspace_id=NULL,cleanup_request_id=create_request_id,current_generation=current_generation+1,last_sequence_no=?," + counter + "=" + counter + "+1,version=version+1 WHERE id=? AND version=? AND status IN ('RESETTING','CLEANUP_PENDING')",
                    sequence,id,version);
            return required(id);
        });
    }
    @Override public SavedAttempt beginClearing(UUID id, long version, UUID cleanupId, int stars) {
        updateOne("UPDATE stage_attempt SET status='CLEARING',cleanup_request_id=?,pending_stars=?,version=version+1 WHERE id=? AND version=? AND status='ACTIVE'", cleanupId,stars,id,version);
        return required(id);
    }
    @Override public SavedAttempt completeClear(UUID id, long version, Instant now) {
        updateOne("UPDATE stage_attempt SET status='CLEARED',workspace_id=NULL,cleanup_request_id=NULL,stars=pending_stars,pending_stars=NULL,completed_at=?,version=version+1 WHERE id=? AND version=? AND status IN ('CLEARING','CLEANUP_PENDING') AND pending_stars IS NOT NULL",
                Timestamp.from(now),id,version);
        return required(id);
    }
    @Override public int highestStars(String stageKey) {
        Integer value = jdbc.queryForObject("SELECT COALESCE(MAX(stars),0) FROM stage_attempt WHERE stage_key=? AND status='CLEARED'", Integer.class, stageKey);
        return value == null ? 0 : value;
    }

    private SavedAttempt required(UUID id) {
        return jdbc.query("SELECT " + COLUMNS + " FROM stage_attempt WHERE id=?", this::map, id).stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("attempt persistence is missing"));
    }
    private SavedAttempt map(ResultSet rs, int row) throws SQLException {
        return new SavedAttempt(rs.getObject("id",UUID.class),rs.getString("status"),rs.getLong("version"),rs.getLong("current_generation"),
                rs.getObject("workspace_id",UUID.class),rs.getObject("create_request_id",UUID.class),rs.getObject("cleanup_request_id",UUID.class),
                (Integer)rs.getObject("pending_stars"),rs.getInt("highest_hint_level"),rs.getInt("player_reset_count"),rs.getInt("system_recovery_count"),
                rs.getLong("last_sequence_no"),(Integer)rs.getObject("stars"));
    }
    private void updateOne(String sql, Object... args) {
        if (jdbc.update(sql,args) != 1) throw new IllegalStateException("attempt persistence conflict");
    }
}
