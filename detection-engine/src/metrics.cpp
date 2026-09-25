#include "metrics.hpp"

#include <sstream>
#include <thread>

#if defined(_WIN32)
#include <winsock2.h>
#else
#include <arpa/inet.h>
#include <netinet/in.h>
#include <sys/socket.h>
#include <unistd.h>
#endif

namespace {
void close_socket(int socket) {
#if defined(_WIN32)
    closesocket(socket);
#else
    close(socket);
#endif
}

void stop_socket(int socket) {
#if defined(_WIN32)
    shutdown(socket, SD_BOTH);
#else
    shutdown(socket, SHUT_RDWR);
#endif
}
}

std::string metrics_payload(const Metrics& metrics) {
    std::ostringstream output;
    output << "# TYPE fraud_engine_transactions_ingested_total counter\n"
           << "fraud_engine_transactions_ingested_total " << metrics.ingested.load() << "\n"
           << "# TYPE fraud_engine_transactions_scored_total counter\n"
           << "fraud_engine_transactions_scored_total " << metrics.scored.load() << "\n"
           << "# TYPE fraud_engine_rule_triggers_total counter\n"
           << "fraud_engine_rule_triggers_total " << metrics.rule_triggers.load() << "\n"
           << "# TYPE fraud_engine_parse_errors_total counter\n"
           << "fraud_engine_parse_errors_total " << metrics.parse_errors.load() << "\n"
           << "# TYPE fraud_engine_queue_depth gauge\n"
           << "fraud_engine_queue_depth " << metrics.queue_depth.load() << "\n"
           << "# TYPE fraud_engine_processing_latency_microseconds gauge\n"
           << "fraud_engine_processing_latency_microseconds " << metrics.processing_latency_us.load() << "\n";
    return output.str();
}

MetricsServer::MetricsServer(Metrics& metrics, unsigned short port) : metrics_(metrics), port_(port) {}
MetricsServer::~MetricsServer() { stop(); }

void MetricsServer::start() {
    if (port_ == 0) {
        return;
    }
    running_ = true;
    thread_ = new std::thread(&MetricsServer::serve, this);
}

void MetricsServer::stop() {
    if (!running_.exchange(false)) {
        return;
    }
    if (server_socket_ >= 0) {
        stop_socket(server_socket_);
        close_socket(server_socket_);
        server_socket_ = -1;
    }
    if (thread_ != nullptr && thread_->joinable()) {
        thread_->join();
    }
    delete thread_;
    thread_ = nullptr;
}

void MetricsServer::serve() {
#if defined(_WIN32)
    WSADATA data;
    WSAStartup(MAKEWORD(2, 2), &data);
#endif
    server_socket_ = static_cast<int>(socket(AF_INET, SOCK_STREAM, 0));
    sockaddr_in address{};
    address.sin_family = AF_INET;
    address.sin_addr.s_addr = htonl(INADDR_ANY);
    address.sin_port = htons(port_);
    int enabled = 1;
    setsockopt(server_socket_, SOL_SOCKET, SO_REUSEADDR, reinterpret_cast<char*>(&enabled), sizeof(enabled));
    if (bind(server_socket_, reinterpret_cast<sockaddr*>(&address), sizeof(address)) < 0 || listen(server_socket_, 8) < 0) {
        return;
    }
    while (running_) {
        const auto client = accept(server_socket_, nullptr, nullptr);
        if (client < 0) {
            continue;
        }
        const std::string body = metrics_payload(metrics_);
        const std::string response = "HTTP/1.1 200 OK\r\nContent-Type: text/plain; version=0.0.4\r\nContent-Length: " +
            std::to_string(body.size()) + "\r\nConnection: close\r\n\r\n" + body;
        send(client, response.data(), static_cast<int>(response.size()), 0);
        close_socket(client);
    }
#if defined(_WIN32)
    WSACleanup();
#endif
}
