#pragma once

#include "ring_buffer.hpp"
#include "transaction.hpp"

#include <cstddef>

struct FeatureVector {
    std::size_t transaction_count{};
    double rolling_mean{};
    double rolling_stddev{};
    double velocity_per_minute{};
    double seconds_since_device_change{};
    double device_geo_distance_km{};
};

struct AccountState {
    RingBuffer<Transaction, 64> transactions;
};

FeatureVector extract_features(const Transaction& transaction, const AccountState& state);
