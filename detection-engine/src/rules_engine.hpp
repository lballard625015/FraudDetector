#pragma once

#include "features.hpp"

#include <nlohmann/json.hpp>

#include <string>

struct RuleConfig {
    bool velocity_enabled{true};
    double velocity_window_seconds{300.0};
    std::size_t velocity_max_transactions{5};
    double velocity_score{0.65};
    bool structuring_enabled{true};
    double structuring_threshold{10000.0};
    double structuring_lower_bound{9000.0};
    double structuring_window_seconds{3600.0};
    std::size_t structuring_min_count{3};
    double structuring_score{0.9};
    bool device_geo_enabled{true};
    double device_geo_max_distance_km{500.0};
    double device_geo_score{0.8};

    static RuleConfig from_file(const std::string& path);
};

struct RuleResult {
    double velocity_score{};
    double structuring_score{};
    double device_geo_score{};
    double combined_score{};
    std::string category;
};

class RuleEngine {
public:
    explicit RuleEngine(RuleConfig config) : config_(config) {}
    RuleResult evaluate(const Transaction& transaction, const AccountState& state) const;

private:
    RuleConfig config_;
};
