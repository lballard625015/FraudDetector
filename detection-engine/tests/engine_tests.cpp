#include "features.hpp"
#include "ring_buffer.hpp"
#include "rules_engine.hpp"

#include <cassert>
#include <chrono>
#include <string>

int main() {
    RingBuffer<int, 3> buffer;
    buffer.push(1);
    buffer.push(2);
    buffer.push(3);
    buffer.push(4);
    assert(buffer.size() == 3);
    assert(buffer.at(0) == 2);
    assert(buffer.at(2) == 4);

    const auto first = Transaction::from_json({
        {"transaction_id", "00000000-0000-0000-0000-000000000001"},
        {"from_account", "00000000-0000-0000-0000-000000000010"},
        {"to_account", "00000000-0000-0000-0000-000000000011"},
        {"amount", "9500"}, {"currency", "USD"},
        {"device_id", "00000000-0000-0000-0000-000000000020"},
        {"geo_lat", "40.7"}, {"geo_lon", "-74.0"},
        {"occurred_at", "2026-01-01T00:00:00Z"}
    });
    const auto second = first;
    RuleConfig config;
    config.structuring_min_count = 2;
    config.velocity_max_transactions = 10;
    RuleEngine rules(config);
    AccountState state;
    state.transactions.push(first);
    const auto result = rules.evaluate(second, state);
    assert(result.structuring_score > 0.0);
    return 0;
}
