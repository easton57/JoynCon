cmake_minimum_required(VERSION 3.22.1)
project(joyncon_native)

add_library(joyncon_native SHARED
    joyncon_native.cpp
)

find_library(log-lib log)

target_link_libraries(joyncon_native
    ${log-lib}
)
