-- Repeatable demo data for account-specific risk charts.
INSERT INTO accounts (account_id, owner_name, created_at, risk_score, status)
VALUES
    ('00000000-0000-0000-0000-000000000481', 'Avery Morgan', now() - interval '30 days', 0.74, 'active'),
    ('00000000-0000-0000-0000-000000000482', 'Jordan Lee', now() - interval '24 days', 0.42, 'active'),
    ('00000000-0000-0000-0000-000000000483', 'Riley Chen', now() - interval '18 days', 0.18, 'active')
ON CONFLICT (account_id) DO UPDATE
SET owner_name = EXCLUDED.owner_name,
    risk_score = EXCLUDED.risk_score,
    status = EXCLUDED.status;

INSERT INTO transactions (
    transaction_id, from_account, to_account, amount, currency, occurred_at,
    is_synthetic_fraud, fraud_pattern_type
)
SELECT
    md5('risk-demo-transaction-' || source.account_id || '-' || sample.index)::uuid,
    source.account_id,
    destination.account_id,
    sample.amount,
    'USD',
    now() - interval '5 hours' + sample.index * interval '45 minutes',
    sample.index >= 5,
    CASE WHEN sample.index >= 5 THEN 'coordinated_burst' ELSE 'normal' END
FROM (
    SELECT '00000000-0000-0000-0000-000000000481'::uuid AS account_id,
           '00000000-0000-0000-0000-000000000482'::uuid AS destination_id,
           ARRAY[120, 180, 240, 310, 420, 740, 880]::numeric[] AS amounts
    UNION ALL
    SELECT '00000000-0000-0000-0000-000000000482'::uuid,
           '00000000-0000-0000-0000-000000000483'::uuid,
           ARRAY[90, 130, 170, 220, 280, 360, 440]::numeric[]
    UNION ALL
    SELECT '00000000-0000-0000-0000-000000000483'::uuid,
           '00000000-0000-0000-0000-000000000481'::uuid,
           ARRAY[70, 80, 110, 95, 130, 150, 180]::numeric[]
) source
JOIN accounts destination ON destination.account_id = source.destination_id
CROSS JOIN LATERAL unnest(source.amounts) WITH ORDINALITY AS sample(amount, index)
ON CONFLICT (transaction_id) DO NOTHING;

INSERT INTO signals (
    signal_id, transaction_id, rule_score, ml_score, graph_score,
    combined_score, category, created_at
)
SELECT
    md5('risk-demo-signal-' || transaction_id)::uuid,
    transaction_id,
    rule_score,
    ml_score,
    graph_score,
    round((rule_score * 0.40 + ml_score * 0.35 + graph_score * 0.25)::numeric, 6),
    category,
    occurred_at
FROM (
    SELECT
        transaction_id,
        occurred_at,
        CASE WHEN amount >= 700 THEN 0.90 ELSE 0.25 + (amount / 1000.0) END AS rule_score,
        CASE WHEN amount >= 700 THEN 0.82 ELSE 0.18 + (amount / 1200.0) END AS ml_score,
        CASE WHEN amount >= 700 THEN 0.76 ELSE 0.12 + (amount / 1400.0) END AS graph_score,
        CASE WHEN amount >= 700 THEN 'coordinated_burst' ELSE 'velocity' END AS category
    FROM transactions
    WHERE transaction_id IN (
        SELECT md5('risk-demo-transaction-' || account_id || '-' || sample.index)::uuid
        FROM accounts source
        CROSS JOIN LATERAL generate_series(1, 7) AS sample(index)
        WHERE source.account_id IN (
            '00000000-0000-0000-0000-000000000481',
            '00000000-0000-0000-0000-000000000482',
            '00000000-0000-0000-0000-000000000483'
        )
    )
) scored
ON CONFLICT (signal_id) DO NOTHING;

INSERT INTO alerts (
    alert_id, transaction_id, rule_score, ml_score, graph_score,
    combined_score, category, severity, created_at, status
)
SELECT
    md5('risk-demo-alert-' || transaction_id)::uuid,
    transaction_id,
    rule_score,
    ml_score,
    graph_score,
    combined_score,
    category,
    CASE WHEN combined_score >= 0.70 THEN 'high' WHEN combined_score >= 0.40 THEN 'medium' ELSE 'low' END,
    occurred_at,
    CASE WHEN combined_score >= 0.70 THEN 'open' ELSE 'resolved' END
FROM (
    SELECT
        transaction_id,
        occurred_at,
        CASE WHEN amount >= 700 THEN 0.90 ELSE 0.25 + (amount / 1000.0) END AS rule_score,
        CASE WHEN amount >= 700 THEN 0.82 ELSE 0.18 + (amount / 1200.0) END AS ml_score,
        CASE WHEN amount >= 700 THEN 0.76 ELSE 0.12 + (amount / 1400.0) END AS graph_score,
        round((CASE WHEN amount >= 700 THEN 0.90 ELSE 0.25 + (amount / 1000.0) END * 0.40 + CASE WHEN amount >= 700 THEN 0.82 ELSE 0.18 + (amount / 1200.0) END * 0.35 + CASE WHEN amount >= 700 THEN 0.76 ELSE 0.12 + (amount / 1400.0) END * 0.25)::numeric, 6) AS combined_score,
        CASE WHEN amount >= 700 THEN 'coordinated_burst' ELSE 'velocity' END AS category
    FROM transactions
    WHERE transaction_id IN (
        SELECT md5('risk-demo-transaction-' || account_id || '-' || sample.index)::uuid
        FROM accounts source
        CROSS JOIN LATERAL generate_series(1, 7) AS sample(index)
        WHERE source.account_id IN (
            '00000000-0000-0000-0000-000000000481',
            '00000000-0000-0000-0000-000000000482',
            '00000000-0000-0000-0000-000000000483'
        )
    )
) scored
ON CONFLICT (alert_id) DO NOTHING;
