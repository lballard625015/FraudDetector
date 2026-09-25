CREATE TABLE IF NOT EXISTS analyst_users (
    analyst_id UUID PRIMARY KEY,
    email TEXT NOT NULL UNIQUE,
    display_name TEXT NOT NULL,
    password_hash TEXT NOT NULL,
    role TEXT NOT NULL DEFAULT 'analyst',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS analyst_sessions (
    token TEXT PRIMARY KEY,
    analyst_id UUID NOT NULL REFERENCES analyst_users(analyst_id) ON DELETE CASCADE,
    expires_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_analyst_sessions_expires_at ON analyst_sessions(expires_at);
