cmake_minimum_required(VERSION 3.22.1)
project(maizosnes LANGUAGES CXX)

add_library(native-lib SHARED native-lib.cpp)

find_library(android-lib android)
find_library(log-lib log)

target_link_libraries(native-lib PRIVATE ${android-lib} ${log-lib})
