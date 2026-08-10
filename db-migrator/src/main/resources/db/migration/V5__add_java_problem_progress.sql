CREATE TABLE java_problem_progress (
    problem_key varchar(64) PRIMARY KEY,
    status varchar(16) NOT NULL CHECK (status IN ('NOT_STARTED','IN_PROGRESS','COMPLETED')),
    updated_at timestamptz NOT NULL
);

GRANT SELECT, INSERT, UPDATE ON java_problem_progress TO developer_dungeon_app;
