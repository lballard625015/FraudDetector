-- Repeatable risk history for every account currently in the database.
-- The fixed UUID prefix makes this safe to run more than once.
INSERT INTO transactions (
    transaction_id, from_account, to_account, amount, currency, occurred_at,
    is_synthetic_fraud, fraud_pattern_type
)
SELECT
    md5('all-account-risk-' || account_record.account_id || '-' || sample.index)::uuid,
    account_record.account_id,
    account_record.account_id,
    CASE
        WHEN sample.index IN (6, 7, 8) THEN 720 + (sample.index * 35)
        ELSE 120 + (sample.index * 42)
    END,
    'USD',
    now() - interval '5 hours' + sample.index * interval '40 minutes',
    sample.index >= 6,
    CASE WHEN sample.index >= 6 THEN 'coordinated_burst' ELSE 'normal' END
FROM accounts account_record
CROSS JOIN LATERAL generate_series(1, 8) AS sample(index)
ON CONFLICT (transaction_id) DO NOTHING;

INSERT INTO signals (
    signal_id, transaction_id, rule_score, ml_score, graph_score,
    combined_score, category, created_at
)
SELECT
    md5('all-account-signal-' || transaction_record.transaction_id)::uuid,
    transaction_record.transaction_id,
    scored.rule_score,
    scored.ml_score,
    scored.graph_score,
    scored.combined_score,
    scored.category,
    transaction_record.occurred_at
FROM transactions transaction_record
CROSS JOIN LATERAL (
    SELECT
        CASE WHEN transaction_record.amount >= 700 THEN 0.88 ELSE 0.22 + transaction_record.amount / 1100.0 END AS rule_score,
        CASE WHEN transaction_record.amount >= 700 THEN 0.81 ELSE 0.16 + transaction_record.amount / 1300.0 END AS ml_score,
        CASE WHEN transaction_record.amount >= 700 THEN 0.75 ELSE 0.12 + transaction_record.amount / 1500.0 END AS graph_score,
        CASE WHEN transaction_record.amount >= 700 THEN 'coordinated_burst' ELSE 'velocity' END AS category
) values_for_score
CROSS JOIN LATERAL (
    SELECT values_for_score.rule_score, values_for_score.ml_score, values_for_score.graph_score,
           round((values_for_score.rule_score * 0.40 + values_for_score.ml_score * 0.35 + values_for_score.graph_score * 0.25)::numeric, 6) AS combined_score,
           values_for_score.category
) scored
WHERE transaction_record.transaction_id::text LIKE '________-____-____-____-____________'
  AND transaction_record.transaction_id IN (
      SELECT md5('all-account-risk-' || account_record.account_id || '-' || sample.index)::uuid
      FROM accounts account_record
      CROSS JOIN LATERAL generate_series(1, 8) AS sample(index)
  )
ON CONFLICT (signal_id) DO NOTHING;

INSERT INTO alerts (
    alert_id, transaction_id, rule_score, ml_score, graph_score,
    combined_score, category, severity, created_at, status
)
SELECT
    md5('all-account-alert-' || transaction_record.transaction_id)::uuid,
    transaction_record.transaction_id,
    scored.rule_score,
    scored.ml_score,
    scored.graph_score,
    scored.combined_score,
    scored.category,
    CASE WHEN scored.combined_score >= 0.70 THEN 'high' WHEN scored.combined_score >= 0.40 THEN 'medium' ELSE 'low' END,
    transaction_record.occurred_at,
    CASE WHEN scored.combined_score >= 0.70 THEN 'open' ELSE 'resolved' END
FROM transactions transaction_record
CROSS JOIN LATERAL (
    SELECT
        CASE WHEN transaction_record.amount >= 700 THEN 0.88 ELSE 0.22 + transaction_record.amount / 1100.0 END AS rule_score,
        CASE WHEN transaction_record.amount >= 700 THEN 0.81 ELSE 0.16 + transaction_record.amount / 1300.0 END AS ml_score,
        CASE WHEN transaction_record.amount >= 700 THEN 0.75 ELSE 0.12 + transaction_record.amount / 1500.0 END AS graph_score,
        CASE WHEN transaction_record.amount >= 700 THEN 'coordinated_burst' ELSE 'velocity' END AS category
) values_for_score
CROSS JOIN LATERAL (
    SELECT values_for_score.rule_score, values_for_score.ml_score, values_for_score.graph_score,
           round((values_for_score.rule_score * 0.40 + values_for_score.ml_score * 0.35 + values_for_score.graph_score * 0.25)::numeric, 6) AS combined_score,
           values_for_score.category
) scored
WHERE transaction_record.transaction_id IN (
      SELECT md5('all-account-risk-' || account_record.account_id || '-' || sample.index)::uuid
      FROM accounts account_record
      CROSS JOIN LATERAL generate_series(1, 8) AS sample(index)
)
ON CONFLICT (alert_id) DO NOTHING;
