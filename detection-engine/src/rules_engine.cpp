#include "rules_engine.hpp"

#include <algorithm>
#include <fstream>
#include <functional>
#include <stdexcept>

namespace {

template <typename Type>
Type read(const nlohmann::json& root, const char* section, const char* key, Type fallback) {
    return root.contains(section) ? root.at(section).value(key, fallback) : fallback;
}

std::size_t count_recent(const Transaction& transaction, const AccountState& state, double window_seconds,
                         const std::function<bool(const Transaction&)>& predicate) {
    std::size_t count = 0;
    state.transactions.for_each([&](const Transaction& item) {
        if (transaction.occurred_at - item.occurred_at <= window_seconds && predicate(item)) {
            ++count;
        }
    });
    return count;
}
}

RuleConfig RuleConfig::from_file(const std::string& path) {
    std::ifstream input(path);
    if (!input) {
        throw std::runtime_error("cannot open rules file: " + path);
    }
    const auto root = nlohmann::json::parse(input);
    RuleConfig config;
    config.velocity_enabled = read(root, "velocity", "enabled", config.velocity_enabled);
    config.velocity_window_seconds = read(root, "velocity", "window_seconds", config.velocity_window_seconds);
    config.velocity_max_transactions = read(root, "velocity", "max_transactions", config.velocity_max_transactions);
    config.velocity_score = read(root, "velocity", "score", config.velocity_score);
    config.structuring_enabled = read(root, "structuring", "enabled", config.structuring_enabled);
    config.structuring_threshold = read(root, "structuring", "threshold", config.structuring_threshold);
    config.structuring_lower_bound = read(root, "structuring", "lower_bound", config.structuring_lower_bound);
    config.structuring_window_seconds = read(root, "structuring", "window_seconds", config.structuring_window_seconds);
    config.structuring_min_count = read(root, "structuring", "min_count", config.structuring_min_count);
    config.structuring_score = read(root, "structuring", "score", config.structuring_score);
    config.device_geo_enabled = read(root, "device_geo_change", "enabled", config.device_geo_enabled);
    config.device_geo_max_distance_km = read(root, "device_geo_change", "max_distance_km", config.device_geo_max_distance_km);
    config.device_geo_score = read(root, "device_geo_change", "score", config.device_geo_score);
    return config;
}

RuleResult RuleEngine::evaluate(const Transaction& transaction, const AccountState& state) const {
    RuleResult result;
    if (config_.velocity_enabled &&
        count_recent(transaction, state, config_.velocity_window_seconds,
                     [&](const Transaction& item) { return item.from_account == transaction.from_account; }) + 1 >
            config_.velocity_max_transactions) {
        result.velocity_score = config_.velocity_score;
        result.category = "velocity";
    }

    if (config_.structuring_enabled) {
        const auto count = count_recent(transaction, state, config_.structuring_window_seconds,
            [&](const Transaction& item) {
                return item.from_account == transaction.from_account &&
                       item.amount >= config_.structuring_lower_bound &&
                       item.amount < config_.structuring_threshold;
            });
        if (count + (transaction.amount >= config_.structuring_lower_bound &&
                     transaction.amount < config_.structuring_threshold ? 1 : 0) >= config_.structuring_min_count) {
            result.structuring_score = config_.structuring_score;
            result.category = "structuring";
        }
    }

    if (config_.device_geo_enabled && !state.transactions.empty()) {
        const auto& previous = state.transactions.at(state.transactions.size() - 1);
        if (previous.device_id != transaction.device_id ||
            extract_features(transaction, state).device_geo_distance_km > config_.device_geo_max_distance_km) {
            result.device_geo_score = config_.device_geo_score;
            result.category = "device_geo_change";
        }
    }

    result.combined_score = std::max({result.velocity_score, result.structuring_score, result.device_geo_score});
    return result;
}
