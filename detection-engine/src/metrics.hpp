#pragma once

#include <atomic>
#include <cstdint>
#include <string>
#include <thread>

struct Metrics {
    std::atomic<std::uint64_t> ingested{0};
    std::atomic<std::uint64_t> scored{0};
    std::atomic<std::uint64_t> rule_triggers{0};
    std::atomic<std::uint64_t> parse_errors{0};
    std::atomic<std::uint64_t> queue_depth{0};
    std::atomic<std::uint64_t> processing_latency_us{0};
};

class MetricsServer {
public:
    MetricsServer(Metrics& metrics, unsigned short port);
    ~MetricsServer();
    void start();
    void stop();

private:
    void serve();
    Metrics& metrics_;
    unsigned short port_;
    int server_socket_{-1};
    std::atomic<bool> running_{false};
    std::thread* thread_{nullptr};
};

std::string metrics_payload(const Metrics& metrics);
