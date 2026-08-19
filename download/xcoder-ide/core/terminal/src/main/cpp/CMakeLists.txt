cmake_minimum_required(VERSION 3.22.1)
project("xcoder_terminal" LANGUAGES C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_CXX_STANDARD 17)

find_library(log-lib log)

add_library(xcoder_terminal SHARED
    pty.cpp
)

target_include_directories(xcoder_terminal PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
)

target_link_libraries(xcoder_terminal
    ${log-lib}
    pthread
)

target_compile_options(xcoder_terminal PRIVATE
    -Wall
    -Wextra
    -O2
)