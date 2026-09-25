#pragma once

#include <nlohmann/json.hpp>

#include <chrono>
#include <cstdint>
#include <ctime>
#include <cmath>
#include <iomanip>
#include <sstream>
#include <stdexcept>
#include <string>

struct Transaction {
    std::string transaction_id;
    std::string from_account;
    std::string to_account;
    double amount{};
    std::string currency;
    std::string device_id;
    double geo_lat{};
    double geo_lon{};
    std::int64_t occurred_at{};
    bool is_synthetic_fraud{};
    std::string fraud_pattern_type;
    nlohmann::json raw;

    static std::int64_t parse_timestamp(const std::string& value) {
        std::tm calendar{};
        std::istringstream input(value.substr(0, 19));
        input >> std::get_time(&calendar, "%Y-%m-%dT%H:%M:%S");
        if (input.fail()) {
            throw std::invalid_argument("invalid occurred_at timestamp: " + value);
        }
#if defined(_WIN32)
        return static_cast<std::int64_t>(_mkgmtime(&calendar));
#else
        return static_cast<std::int64_t>(timegm(&calendar));
#endif
    }

    static std::string nullable_string(const nlohmann::json& value, const char* key,
                                       const std::string& fallback = "") {
        if (!value.contains(key) || value.at(key).is_null()) {
            return fallback;
        }
        if (!value.at(key).is_string()) {
            throw std::invalid_argument(std::string("field must be a string: ") + key);
        }
        return value.at(key).get<std::string>();
    }

    static double nullable_number(const nlohmann::json& value, const char* key, double fallback = 0.0) {
        if (!value.contains(key) || value.at(key).is_null()) {
            return fallback;
        }
        if (value.at(key).is_number()) {
            return value.at(key).get<double>();
        }
        if (value.at(key).is_string()) {
            return std::stod(value.at(key).get<std::string>());
        }
        throw std::invalid_argument(std::string("field must be numeric: ") + key);
    }

    static Transaction from_json(const nlohmann::json& value) {
        Transaction transaction;
        transaction.raw = value;
        transaction.transaction_id = nullable_string(value, "transaction_id");
        transaction.from_account = nullable_string(value, "from_account");
        transaction.to_account = nullable_string(value, "to_account");
        transaction.amount = nullable_number(value, "amount");
        transaction.currency = nullable_string(value, "currency", "USD");
        transaction.device_id = nullable_string(value, "device_id");
        transaction.geo_lat = nullable_number(value, "geo_lat");
        transaction.geo_lon = nullable_number(value, "geo_lon");
        transaction.occurred_at = parse_timestamp(nullable_string(value, "occurred_at"));
        transaction.is_synthetic_fraud = value.value("is_synthetic_fraud", false);
        transaction.fraud_pattern_type = nullable_string(value, "fraud_pattern_type");
        return transaction;
    }
};
