#include "bounded_queue.hpp"
#include "features.hpp"
#include "metrics.hpp"
#include "rules_engine.hpp"
#include "transaction.hpp"

#include <nlohmann/json.hpp>
#include <librdkafka/rdkafkacpp.h>

#include <algorithm>
#include <atomic>
#include <chrono>
#include <cstdlib>
#include <fstream>
#include <iostream>
#include <map>
#include <memory>
#include <mutex>
#include <optional>
#include <stdexcept>
#include <string>
#include <thread>
#include <vector>

#if !defined(_WIN32)
#include <netinet/in.h>
#include <sys/socket.h>
#include <unistd.h>
#endif

struct Options {
    std::string input{"-"};
    std::string output{"-"};
    std::string rules{"config/rules.json"};
    unsigned short socket_port{9100};
    unsigned short metrics_port{9101};
    std::size_t workers{2};
    std::string kafka_brokers{"localhost:9092"};
    std::string kafka_input_topic{"transactions"};
    std::string kafka_output_topic{"scored-events"};
    std::string kafka_group{"detection-engine"};
    std::size_t max_messages{};
    int idle_timeout_ms{1000};
};

Options parse_options(int argc, char** argv) {
    Options options;
    for (int index = 1; index < argc; ++index) {
        const std::string argument = argv[index];
        auto value = [&]() { return index + 1 < argc ? std::string(argv[++index]) : std::string{}; };
        if (argument == "--input") options.input = value();
        else if (argument == "--output") options.output = value();
        else if (argument == "--rules") options.rules = value();
        else if (argument == "--socket-port") options.socket_port = static_cast<unsigned short>(std::stoi(value()));
        else if (argument == "--metrics-port") options.metrics_port = static_cast<unsigned short>(std::stoi(value()));
        else if (argument == "--workers") options.workers = std::max<std::size_t>(1, std::stoul(value()));
        else if (argument == "--kafka-brokers") options.kafka_brokers = value();
        else if (argument == "--kafka-input-topic") options.kafka_input_topic = value();
        else if (argument == "--kafka-output-topic") options.kafka_output_topic = value();
        else if (argument == "--kafka-group") options.kafka_group = value();
        else if (argument == "--max-messages") options.max_messages = std::stoul(value());
        else if (argument == "--idle-timeout-ms") options.idle_timeout_ms = std::stoi(value());
        else if (argument == "--help") {
            std::cout << "Usage: detection-engine [--input FILE|socket|kafka] [--output FILE|-|kafka] "
                         "[--rules FILE] [--workers N] [--kafka-brokers HOST:PORT] "
                         "[--kafka-input-topic TOPIC] [--kafka-output-topic TOPIC] "
                         "[--kafka-group GROUP] [--max-messages N]\n";
            std::exit(0);
        }
    }
    return options;
}

class Engine {
public:
    Engine(const Options& options, RuleEngine rules, Metrics& metrics)
        : options_(options), rules_(std::move(rules)), metrics_(metrics), queue_(256) {}

    void run() {
        if (options_.input == "kafka") {
            kafka_consumer_ = create_consumer();
        }
        if (options_.output == "kafka") {
            kafka_producer_ = create_producer();
        }
        metrics_server_.emplace(metrics_, options_.metrics_port);
        metrics_server_->start();
        std::vector<std::thread> workers;
        for (std::size_t index = 0; index < options_.workers; ++index) {
            workers.emplace_back(&Engine::score_loop, this);
        }
        ingest_loop();
        queue_.close();
        for (auto& worker : workers) worker.join();
        if (kafka_producer_) {
            kafka_producer_->flush(5000);
        }
        if (kafka_consumer_) {
            kafka_consumer_->close();
        }
        metrics_server_->stop();
    }

private:
    std::unique_ptr<RdKafka::KafkaConsumer> create_consumer() {
        std::string error;
        auto* config = RdKafka::Conf::create(RdKafka::Conf::CONF_GLOBAL);
        if (config->set("bootstrap.servers", options_.kafka_brokers, error) != RdKafka::Conf::CONF_OK ||
            config->set("group.id", options_.kafka_group, error) != RdKafka::Conf::CONF_OK ||
            config->set("enable.auto.commit", "false", error) != RdKafka::Conf::CONF_OK ||
            config->set("auto.offset.reset", "earliest", error) != RdKafka::Conf::CONF_OK) {
            delete config;
            throw std::runtime_error("Kafka consumer configuration failed: " + error);
        }
        auto consumer = RdKafka::KafkaConsumer::create(config, error);
        delete config;
        if (!consumer) {
            throw std::runtime_error("Kafka consumer creation failed: " + error);
        }
        RdKafka::ErrorCode subscribe_error = consumer->subscribe({options_.kafka_input_topic});
        if (subscribe_error != RdKafka::ERR_NO_ERROR) {
            throw std::runtime_error("Kafka subscribe failed: " + RdKafka::err2str(subscribe_error));
        }
        return std::unique_ptr<RdKafka::KafkaConsumer>(consumer);
    }

    std::unique_ptr<RdKafka::Producer> create_producer() {
        std::string error;
        auto* config = RdKafka::Conf::create(RdKafka::Conf::CONF_GLOBAL);
        if (config->set("bootstrap.servers", options_.kafka_brokers, error) != RdKafka::Conf::CONF_OK) {
            delete config;
            throw std::runtime_error("Kafka producer configuration failed: " + error);
        }
        auto producer = RdKafka::Producer::create(config, error);
        delete config;
        if (!producer) {
            throw std::runtime_error("Kafka producer creation failed: " + error);
        }
        return std::unique_ptr<RdKafka::Producer>(producer);
    }

    void ingest_loop() {
        if (options_.input == "kafka") {
            std::size_t received = 0;
            while (options_.max_messages == 0 || received < options_.max_messages) {
                std::unique_ptr<RdKafka::Message> message(kafka_consumer_->consume(options_.idle_timeout_ms));
                if (message->err() == RdKafka::ERR__TIMED_OUT) {
                    continue;
                }
                if (message->err() != RdKafka::ERR_NO_ERROR) {
                    ++metrics_.parse_errors;
                    std::cerr << "Kafka input error: " << message->errstr() << '\n';
                    continue;
                }
                ingest_line(std::string(static_cast<const char*>(message->payload()), message->len()));
                kafka_consumer_->commitAsync(message.get());
                ++received;
            }
            return;
        }
        if (options_.input == "socket") {
#if defined(_WIN32)
            std::cerr << "Socket input is currently supported in the Linux container build.\n";
            return;
#else
            const auto server = socket(AF_INET, SOCK_STREAM, 0);
            sockaddr_in address{};
            address.sin_family = AF_INET;
            address.sin_addr.s_addr = htonl(INADDR_ANY);
            address.sin_port = htons(options_.socket_port);
            int enabled = 1;
            setsockopt(server, SOL_SOCKET, SO_REUSEADDR, &enabled, sizeof(enabled));
            if (bind(server, reinterpret_cast<sockaddr*>(&address), sizeof(address)) < 0 || listen(server, 1) < 0) {
                throw std::runtime_error("cannot bind input socket");
            }
            const auto client = accept(server, nullptr, nullptr);
            if (client >= 0) {
                std::string line;
                char character;
                while (read(client, &character, 1) == 1) {
                    if (character == '\n') { ingest_line(line); line.clear(); }
                    else line += character;
                }
                if (!line.empty()) ingest_line(line);
                close(client);
            }
            close(server);
#endif
            return;
        }
        std::ifstream file;
        std::istream* input = &std::cin;
        if (options_.input != "-") {
            file.open(options_.input);
            if (!file) throw std::runtime_error("cannot open input: " + options_.input);
            input = &file;
        }
        std::string line;
        while (std::getline(*input, line)) ingest_line(line);
    }

    void ingest_line(const std::string& line) {
        if (line.empty()) return;
        try {
            queue_.push(Transaction::from_json(nlohmann::json::parse(line)));
            ++metrics_.ingested;
            metrics_.queue_depth = queue_.size();
        } catch (const std::exception& error) {
            ++metrics_.parse_errors;
            std::cerr << "Input error: " << error.what() << '\n';
        }
    }

    void score_loop() {
        Transaction transaction;
        while (queue_.pop(transaction)) {
            const auto started = std::chrono::steady_clock::now();
            FeatureVector features;
            RuleResult rules;
            {
                std::lock_guard lock(state_mutex_);
                auto& state = account_states_[transaction.from_account];
                features = extract_features(transaction, state);
                rules = rules_.evaluate(transaction, state);
                state.transactions.push(transaction);
            }
            nlohmann::json scored = transaction.raw;
            scored["rule_score"] = rules.combined_score;
            scored["velocity_score"] = rules.velocity_score;
            scored["structuring_score"] = rules.structuring_score;
            scored["device_geo_score"] = rules.device_geo_score;
            scored["category"] = rules.category;
            scored["features"] = {
                {"transaction_count", features.transaction_count},
                {"rolling_mean", features.rolling_mean},
                {"rolling_stddev", features.rolling_stddev},
                {"velocity_per_minute", features.velocity_per_minute},
                {"seconds_since_device_change", features.seconds_since_device_change},
                {"device_geo_distance_km", features.device_geo_distance_km}
            };
            {
                std::lock_guard lock(output_mutex_);
                if (options_.output == "-") std::cout << scored.dump() << '\n';
                else if (options_.output == "kafka") {
                    const auto payload = scored.dump();
                    const auto result = kafka_producer_->produce(
                        options_.kafka_output_topic, RdKafka::Topic::PARTITION_UA,
                        RdKafka::Producer::RK_MSG_COPY, const_cast<char*>(payload.data()), payload.size(),
                        nullptr, 0, 0, nullptr);
                    if (result != RdKafka::ERR_NO_ERROR) {
                        std::cerr << "Kafka output error: " << RdKafka::err2str(result) << '\n';
                    }
                    kafka_producer_->poll(0);
                }
                else { std::ofstream output(options_.output, std::ios::app); output << scored.dump() << '\n'; }
            }
            ++metrics_.scored;
            if (rules.combined_score > 0.0) ++metrics_.rule_triggers;
            metrics_.processing_latency_us = std::chrono::duration_cast<std::chrono::microseconds>(
                std::chrono::steady_clock::now() - started).count();
            metrics_.queue_depth = queue_.size();
        }
    }

    const Options& options_;
    RuleEngine rules_;
    Metrics& metrics_;
    BoundedQueue<Transaction> queue_;
    std::mutex state_mutex_;
    std::mutex output_mutex_;
    std::map<std::string, AccountState> account_states_;
    std::optional<MetricsServer> metrics_server_;
    std::unique_ptr<RdKafka::KafkaConsumer> kafka_consumer_;
    std::unique_ptr<RdKafka::Producer> kafka_producer_;
};

int main(int argc, char** argv) {
    try {
        const auto options = parse_options(argc, argv);
        Metrics metrics;
        Engine engine(options, RuleEngine(RuleConfig::from_file(options.rules)), metrics);
        engine.run();
    } catch (const std::exception& error) {
        std::cerr << "Detection engine failed: " << error.what() << '\n';
        return 1;
    }
}
