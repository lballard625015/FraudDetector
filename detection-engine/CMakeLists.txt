cmake_minimum_required(VERSION 3.20)
project(detection_engine LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

include(FetchContent)
FetchContent_Declare(
	nlohmann_json
	URL https://github.com/nlohmann/json/releases/download/v3.11.3/json.tar.xz
)
FetchContent_MakeAvailable(nlohmann_json)

find_package(PkgConfig REQUIRED)
pkg_check_modules(RDKAFKA REQUIRED IMPORTED_TARGET rdkafka++>=2.0)

add_executable(detection-engine
	src/main.cpp
	src/features.cpp
	src/rules_engine.cpp
	src/metrics.cpp
)
target_include_directories(detection-engine PRIVATE src)
target_link_libraries(detection-engine PRIVATE nlohmann_json::nlohmann_json PkgConfig::RDKAFKA)
if (WIN32)
	target_link_libraries(detection-engine PRIVATE ws2_32)
endif()

include(CTest)
if (BUILD_TESTING)
	add_executable(detection-engine-tests tests/engine_tests.cpp src/features.cpp src/rules_engine.cpp)
	target_include_directories(detection-engine-tests PRIVATE src)
	target_link_libraries(detection-engine-tests PRIVATE nlohmann_json::nlohmann_json)
	add_test(NAME detection-engine-tests COMMAND detection-engine-tests)
endif()
