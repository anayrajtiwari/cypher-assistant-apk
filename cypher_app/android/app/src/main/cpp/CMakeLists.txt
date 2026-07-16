cmake_minimum_required(VERSION 3.22)
project("cypher_brain")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

include(FetchContent)
FetchContent_Declare(
    llama.cpp
    GIT_REPOSITORY https://github.com/ggerganov/llama.cpp.git
    GIT_TAG master
    GIT_SHALLOW TRUE
)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "")
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "")
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "")
FetchContent_MakeAvailable(llama.cpp)

add_library(cypher_brain SHARED llama_jni.cpp)
target_link_libraries(cypher_brain PRIVATE llama)
target_include_directories(cypher_brain PRIVATE ${llama.cpp_SOURCE_DIR})
