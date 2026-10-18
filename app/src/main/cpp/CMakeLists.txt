cmake_minimum_required(VERSION 3.22.1)
project(maizosnes LANGUAGES CXX)

add_library(native-lib SHARED native-lib.cpp)

set_target_properties(native-lib PROPERTIES
    CXX_STANDARD 17
    CXX_STANDARD_REQUIRED ON
)

target_compile_options(native-lib PRIVATE -O2)

find_library(android_lib android)
find_library(log_lib log)

target_link_libraries(native-lib PRIVATE ${android_lib} ${log_lib})
