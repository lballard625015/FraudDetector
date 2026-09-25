#pragma once

#include <array>
#include <cstddef>
#include <stdexcept>

// Fixed storage keeps account windows bounded and makes eviction O(1).
template <typename T, std::size_t Capacity>
class RingBuffer {
public:
    void push(const T& value) {
        values_[next_] = value;
        next_ = (next_ + 1) % Capacity;
        if (size_ < Capacity) {
            ++size_;
        }
    }

    [[nodiscard]] std::size_t size() const { return size_; }
    [[nodiscard]] bool empty() const { return size_ == 0; }

    const T& at(std::size_t index) const {
        if (index >= size_) {
            throw std::out_of_range("ring buffer index");
        }
        const auto first = size_ == Capacity ? next_ : 0;
        return values_[(first + index) % Capacity];
    }

    template <typename Function>
    void for_each(Function&& function) const {
        for (std::size_t index = 0; index < size_; ++index) {
            function(at(index));
        }
    }

private:
    std::array<T, Capacity> values_{};
    std::size_t next_{};
    std::size_t size_{};
};
