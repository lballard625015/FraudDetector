CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- Core account and device identity tables used by transaction ingestion.
CREATE TABLE accounts (
    account_id UUID PRIMARY KEY,
    owner_name TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    risk_score NUMERIC NOT NULL DEFAULT 0,
    status TEXT NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'frozen', 'closed'))
);

CREATE TABLE devices (
    device_id UUID PRIMARY KEY,
    fingerprint TEXT NOT NULL,
    first_seen TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE account_devices (
    account_id UUID NOT NULL REFERENCES accounts(account_id),
    device_id UUID NOT NULL REFERENCES devices(device_id),
    linked_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (account_id, device_id)
);

-- Transactions retain synthetic fraud labels so later phases can evaluate detection quality.
CREATE TABLE transactions (
    transaction_id UUID PRIMARY KEY,
    from_account UUID REFERENCES accounts(account_id),
    to_account UUID REFERENCES accounts(account_id),
    amount NUMERIC NOT NULL CHECK (amount >= 0),
    currency TEXT NOT NULL DEFAULT 'USD',
    device_id UUID REFERENCES devices(device_id),
    geo_lat NUMERIC,
    geo_lon NUMERIC,
    occurred_at TIMESTAMPTZ NOT NULL,
    is_synthetic_fraud BOOLEAN NOT NULL DEFAULT FALSE,
    fraud_pattern_type TEXT
);

-- Detection output and analyst workflow tables are created now for the later services.
CREATE TABLE alerts (
    alert_id UUID PRIMARY KEY,
    transaction_id UUID REFERENCES transactions(transaction_id),
    rule_score NUMERIC,
    ml_score NUMERIC,
    graph_score NUMERIC,
    combined_score NUMERIC,
    category TEXT,
    severity TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    status TEXT NOT NULL DEFAULT 'open'
);

-- Raw scoring detections before thresholding, deduplication, or analyst promotion.
CREATE TABLE signals (
    signal_id UUID PRIMARY KEY,
    transaction_id UUID REFERENCES transactions(transaction_id),
    rule_score NUMERIC,
    ml_score NUMERIC,
    graph_score NUMERIC,
    combined_score NUMERIC,
    category TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE cases (
    case_id UUID PRIMARY KEY,
    alert_id UUID REFERENCES alerts(alert_id),
    assigned_to TEXT,
    opened_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    closed_at TIMESTAMPTZ,
    status TEXT NOT NULL DEFAULT 'open',
    resolution TEXT
);

-- Hashes are computed by the Java audit service; the database stores the chain values.
CREATE TABLE case_events (
    event_id UUID PRIMARY KEY,
    case_id UUID NOT NULL REFERENCES cases(case_id),
    event_type TEXT NOT NULL,
    payload JSONB NOT NULL,
    prev_hash TEXT NOT NULL,
    event_hash TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_transactions_occurred_at ON transactions (occurred_at);
CREATE INDEX idx_transactions_from_account ON transactions (from_account);
CREATE INDEX idx_transactions_to_account ON transactions (to_account);
CREATE INDEX idx_alerts_status ON alerts (status);
CREATE INDEX idx_signals_created_at ON signals (created_at);
CREATE INDEX idx_case_events_case_created ON case_events (case_id, created_at);
