#include "features.hpp"

#include <algorithm>
#include <cmath>

namespace {
constexpr double kEarthRadiusKm = 6371.0;
constexpr double radians(double degrees) { return degrees * 3.141592653589793 / 180.0; }

double haversine(double first_lat, double first_lon, double second_lat, double second_lon) {
    const auto delta_lat = radians(second_lat - first_lat);
    const auto delta_lon = radians(second_lon - first_lon);
    const auto a = std::pow(std::sin(delta_lat / 2.0), 2.0) +
                   std::cos(radians(first_lat)) * std::cos(radians(second_lat)) *
                   std::pow(std::sin(delta_lon / 2.0), 2.0);
    return kEarthRadiusKm * 2.0 * std::asin(std::sqrt(a));
}
}

FeatureVector extract_features(const Transaction& transaction, const AccountState& state) {
    FeatureVector features;
    features.transaction_count = state.transactions.size() + 1;
    if (state.transactions.empty()) {
        return features;
    }

    double sum = 0.0;
    double squared_sum = 0.0;
    const auto& oldest = state.transactions.at(0);
    const auto& latest = state.transactions.at(state.transactions.size() - 1);
    state.transactions.for_each([&](const Transaction& item) {
        sum += item.amount;
        squared_sum += item.amount * item.amount;
    });
    features.rolling_mean = sum / static_cast<double>(state.transactions.size());
    const auto variance = squared_sum / static_cast<double>(state.transactions.size()) -
                          features.rolling_mean * features.rolling_mean;
    features.rolling_stddev = std::sqrt(std::max(0.0, variance));
    const auto elapsed_minutes = std::max(1.0, (transaction.occurred_at - oldest.occurred_at) / 60.0);
    features.velocity_per_minute = features.transaction_count / elapsed_minutes;
    features.seconds_since_device_change = latest.device_id == transaction.device_id
        ? static_cast<double>(transaction.occurred_at - latest.occurred_at)
        : 0.0;
    features.device_geo_distance_km = haversine(
        latest.geo_lat, latest.geo_lon, transaction.geo_lat, transaction.geo_lon);
    return features;
}
