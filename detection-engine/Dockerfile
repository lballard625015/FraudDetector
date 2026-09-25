FROM ubuntu:24.04 AS build
RUN apt-get update && apt-get install -y --no-install-recommends build-essential cmake ca-certificates git pkg-config librdkafka-dev && rm -rf /var/lib/apt/lists/*
WORKDIR /src
COPY . .
RUN cmake -S . -B build -DCMAKE_BUILD_TYPE=Release -DBUILD_TESTING=ON && cmake --build build --parallel && ctest --test-dir build --output-on-failure

FROM ubuntu:24.04
RUN apt-get update && apt-get install -y --no-install-recommends ca-certificates librdkafka1 librdkafka++1 && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /src/build/detection-engine .
COPY config ./config
EXPOSE 9101
ENTRYPOINT ["./detection-engine"]
