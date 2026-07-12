package jp.yuya.dev.developerdungeon.app;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

final class MemoryStagePersistence implements StagePersistence {
    private final Map<UUID, SavedAttempt> attempts = new LinkedHashMap<>();
    private final Map<UUID, Boolean> requests = new LinkedHashMap<>();

    @Override public SavedAttempt createStarting(UUID id, String stage, UUID create, Instant now) {
        if (findOpen(stage).isPresent()) throw new IllegalStateException("attempt persistence conflict");
        return put(new SavedAttempt(id,"STARTING",0,0,null,create,create,null,0,0,0,0,null));
    }
    @Override public SavedAttempt workspaceCreated(UUID id,long version,UUID workspace) { return change(id,version,a -> copy(a,"STARTING",workspace,a.createRequestId(),null,null,a.generation(),a.highestHint(),a.playerResets(),a.systemRecoveries(),a.lastSequence(),a.stars())); }
    @Override public SavedAttempt activate(UUID id,long version,UUID workspace) { return change(id,version,a -> copy(a,"ACTIVE",workspace,null,null,null,a.generation(),a.highestHint(),a.playerResets(),a.systemRecoveries(),a.lastSequence(),a.stars())); }
    @Override public Optional<SavedAttempt> findOpen(String stage) { return attempts.values().stream().filter(a -> !terminal(a.status())).findFirst(); }
    @Override public SavedAttempt increaseHint(UUID id,long version,int hint) { return change(id,version,a -> copy(a,a.status(),a.workspaceId(),a.createRequestId(),a.cleanupRequestId(),a.pendingStars(),a.generation(),Math.max(a.highestHint(),hint),a.playerResets(),a.systemRecoveries(),a.lastSequence(),a.stars())); }
    @Override public boolean beginCommand(UUID id,long version,UUID request,long sequence,long generation,String text,String kind,Instant now) {
        if (requests.putIfAbsent(request,Boolean.TRUE) != null) return false;
        change(id,version,a -> copy(a,"EXECUTING",a.workspaceId(),a.createRequestId(),a.cleanupRequestId(),a.pendingStars(),a.generation(),a.highestHint(),a.playerResets(),a.systemRecoveries(),sequence,a.stars())); return true;
    }
    @Override public SavedAttempt finishCommand(UUID id,long version,UUID request,String result,Integer exit,long duration,UUID cleanup,Integer pending) { return change(id,version,a -> copy(a,pending == null ? "ACTIVE" : "CLEARING",a.workspaceId(),pending == null ? a.createRequestId() : null,cleanup,pending,a.generation(),a.highestHint(),a.playerResets(),a.systemRecoveries(),a.lastSequence(),a.stars())); }
    @Override public SavedAttempt recordRejected(UUID id,long version,UUID request,long sequence,long generation,String reason,Instant now) {
        if (requests.putIfAbsent(request,Boolean.TRUE) != null) return required(id);
        return change(id,version,a -> copy(a,a.status(),a.workspaceId(),a.createRequestId(),a.cleanupRequestId(),a.pendingStars(),a.generation(),a.highestHint(),a.playerResets(),a.systemRecoveries(),sequence,a.stars()));
    }
    @Override public SavedAttempt prepareSystemRecovery(UUID id,long version,UUID cleanup,UUID create) {
        SavedAttempt a=required(id); if (!a.status().equals("ACTIVE")&&!a.status().equals("EXECUTING")) return a;
        return change(id,version,x -> copy(x,"RESETTING",x.workspaceId(),create,cleanup,null,x.generation(),x.highestHint(),x.playerResets(),x.systemRecoveries(),x.lastSequence(),x.stars()));
    }
    @Override public SavedAttempt beginReset(UUID id,long version,UUID cleanup,UUID create) { return change(id,version,a -> copy(a,"RESETTING",a.workspaceId(),create,cleanup,null,a.generation(),a.highestHint(),a.playerResets(),a.systemRecoveries(),a.lastSequence(),a.stars())); }
    @Override public SavedAttempt beginCreateCleanup(UUID id,long version,UUID workspace) { return change(id,version,a -> copy(a,"CLEANUP_PENDING",workspace,a.createRequestId(),a.cleanupRequestId(),null,a.generation(),a.highestHint(),a.playerResets(),a.systemRecoveries(),a.lastSequence(),a.stars())); }
    @Override public SavedAttempt markCleanupPending(UUID id,long version) { return change(id,version,a -> copy(a,"CLEANUP_PENDING",a.workspaceId(),a.createRequestId(),a.cleanupRequestId(),a.pendingStars(),a.generation(),a.highestHint(),a.playerResets(),a.systemRecoveries(),a.lastSequence(),a.stars())); }
    @Override public SavedAttempt restartStartingAfterCleanup(UUID id,long version) { return change(id,version,a -> copy(a,"STARTING",null,a.createRequestId(),a.createRequestId(),null,a.generation(),a.highestHint(),a.playerResets(),a.systemRecoveries(),a.lastSequence(),a.stars())); }
    @Override public SavedAttempt completeReset(UUID id,long version,boolean system,UUID history,Instant now) { return change(id,version,a -> copy(a,"STARTING",null,a.createRequestId(),a.createRequestId(),null,a.generation()+1,a.highestHint(),a.playerResets()+(system?0:1),a.systemRecoveries()+(system?1:0),a.lastSequence()+1,a.stars())); }
    @Override public SavedAttempt beginClearing(UUID id,long version,UUID cleanup,int stars) { return change(id,version,a -> copy(a,"CLEARING",a.workspaceId(),null,cleanup,stars,a.generation(),a.highestHint(),a.playerResets(),a.systemRecoveries(),a.lastSequence(),a.stars())); }
    @Override public SavedAttempt completeClear(UUID id,long version,Instant now) { SavedAttempt a=required(id); return change(id,version,x -> copy(x,"CLEARED",null,null,null,null,x.generation(),x.highestHint(),x.playerResets(),x.systemRecoveries(),x.lastSequence(),a.pendingStars())); }
    @Override public int highestStars(String stage) { return attempts.values().stream().filter(a -> a.status().equals("CLEARED")&&a.stars()!=null).mapToInt(SavedAttempt::stars).max().orElse(0); }

    private SavedAttempt change(UUID id,long version,java.util.function.Function<SavedAttempt,SavedAttempt> f) { SavedAttempt a=required(id); if(a.version()!=version) throw new IllegalStateException("attempt persistence conflict"); SavedAttempt next=f.apply(a); next=new SavedAttempt(next.id(),next.status(),version+1,next.generation(),next.workspaceId(),next.createRequestId(),next.cleanupRequestId(),next.pendingStars(),next.highestHint(),next.playerResets(),next.systemRecoveries(),next.lastSequence(),next.stars()); return put(next); }
    private SavedAttempt copy(SavedAttempt a,String status,UUID workspace,UUID create,UUID cleanup,Integer pending,long generation,int hint,int resets,int recoveries,long sequence,Integer stars) { return new SavedAttempt(a.id(),status,a.version(),generation,workspace,create,cleanup,pending,hint,resets,recoveries,sequence,stars); }
    private SavedAttempt put(SavedAttempt a){attempts.put(a.id(),a);return a;} private SavedAttempt required(UUID id){SavedAttempt a=attempts.get(id);if(a==null)throw new IllegalStateException("attempt persistence is missing");return a;} private boolean terminal(String s){return s.equals("CLEARED")||s.equals("FAILED")||s.equals("EXPIRED")||s.equals("ABANDONED");}
}
