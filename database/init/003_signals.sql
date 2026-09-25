CREATE TABLE IF NOT EXISTS signals (
    signal_id UUID PRIMARY KEY,
    transaction_id UUID REFERENCES transactions(transaction_id),
    rule_score NUMERIC,
    ml_score NUMERIC,
    graph_score NUMERIC,
    combined_score NUMERIC,
    category TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_signals_created_at ON signals(created_at);