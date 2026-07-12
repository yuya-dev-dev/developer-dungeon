CREATE TABLE stage_attempt (
    id uuid PRIMARY KEY,
    stage_key varchar(32) NOT NULL CHECK (stage_key ~ '^STAGE-GIT-[0-9]{2}$'),
    status varchar(24) NOT NULL CHECK (status IN ('STARTING','ACTIVE','EXECUTING','CLEARING','RESETTING','CLEANUP_PENDING','CLEARED','FAILED','EXPIRED','ABANDONED')),
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    current_generation bigint NOT NULL CHECK (current_generation >= 0),
    workspace_id uuid,
    create_request_id uuid,
    cleanup_request_id uuid,
    pending_stars smallint CHECK (pending_stars BETWEEN 1 AND 3),
    started_at timestamptz NOT NULL,
    completed_at timestamptz,
    highest_hint_level smallint NOT NULL DEFAULT 0 CHECK (highest_hint_level BETWEEN 0 AND 4),
    player_reset_count integer NOT NULL DEFAULT 0 CHECK (player_reset_count >= 0),
    system_recovery_count integer NOT NULL DEFAULT 0 CHECK (system_recovery_count >= 0),
    stars smallint CHECK (stars BETWEEN 1 AND 3),
    last_sequence_no bigint NOT NULL DEFAULT 0 CHECK (last_sequence_no >= 0),
    CHECK (status <> 'STARTING' OR (create_request_id IS NOT NULL AND cleanup_request_id IS NOT NULL AND pending_stars IS NULL)),
    CHECK (status NOT IN ('ACTIVE','EXECUTING','CLEARING','RESETTING','CLEANUP_PENDING') OR workspace_id IS NOT NULL),
    CHECK (status NOT IN ('ACTIVE','EXECUTING') OR (create_request_id IS NULL AND cleanup_request_id IS NULL AND pending_stars IS NULL)),
    CHECK (status <> 'CLEARING' OR (create_request_id IS NULL AND cleanup_request_id IS NOT NULL AND pending_stars IS NOT NULL)),
    CHECK (status <> 'RESETTING' OR (create_request_id IS NOT NULL AND cleanup_request_id IS NOT NULL AND pending_stars IS NULL)),
    CHECK (status <> 'CLEANUP_PENDING' OR cleanup_request_id IS NOT NULL),
    CHECK (pending_stars IS NULL OR status IN ('CLEARING','CLEANUP_PENDING')),
    CHECK ((status = 'CLEARED') = (stars IS NOT NULL)),
    CHECK (status NOT IN ('CLEARED','FAILED','EXPIRED','ABANDONED') OR (workspace_id IS NULL AND create_request_id IS NULL AND cleanup_request_id IS NULL AND pending_stars IS NULL)),
    CHECK ((status IN ('CLEARED','FAILED','EXPIRED','ABANDONED')) = (completed_at IS NOT NULL))
);

CREATE UNIQUE INDEX uq_stage_attempt_one_open_stage
    ON stage_attempt(stage_key)
    WHERE status NOT IN ('CLEARED','FAILED','EXPIRED','ABANDONED');

CREATE TABLE command_history (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    attempt_id uuid NOT NULL REFERENCES stage_attempt(id) ON DELETE RESTRICT,
    request_id uuid NOT NULL UNIQUE,
    sequence_no bigint NOT NULL CHECK (sequence_no > 0),
    workspace_generation bigint NOT NULL CHECK (workspace_generation >= 0),
    entered_text varchar(256),
    command_kind varchar(32) CHECK (command_kind IN ('STATUS','LOG_ONELINE','SHOW','REVERT_NO_EDIT')),
    result_kind varchar(24) NOT NULL CHECK (result_kind IN ('PENDING','REJECTED','GIT_ERROR','SUCCEEDED','RESET','TIMEOUT','RUNNER_ERROR')),
    reason_code varchar(32) CHECK (reason_code IN ('INVALID_SYNTAX','UNKNOWN_COMMAND','INVALID_ARGUMENT','OBJECT_NOT_ALLOWED')),
    exit_code integer,
    duration_ms bigint CHECK (duration_ms >= 0),
    executed_at timestamptz NOT NULL,
    UNIQUE (attempt_id, sequence_no),
    CHECK ((result_kind = 'REJECTED') = (reason_code IS NOT NULL)),
    CHECK (result_kind <> 'REJECTED' OR (entered_text IS NULL AND command_kind IS NULL)),
    CHECK (result_kind IN ('REJECTED','RESET') OR command_kind IS NOT NULL),
    CHECK (result_kind <> 'PENDING' OR (exit_code IS NULL AND duration_ms IS NULL)),
    CHECK (result_kind NOT IN ('SUCCEEDED','GIT_ERROR','TIMEOUT','RUNNER_ERROR') OR duration_ms IS NOT NULL)
);

GRANT USAGE ON SCHEMA developer_dungeon TO developer_dungeon_app;
GRANT SELECT, INSERT, UPDATE ON stage_attempt, command_history TO developer_dungeon_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA developer_dungeon TO developer_dungeon_app;
