cmake_minimum_required(VERSION 3.22)
project(leanmark_core_tests C CXX)

# Builds the desktop core tests against the copy of the core that ships in the
# app, so the Android build is checked with the desktop's own expectations.
set(CPP ${CMAKE_CURRENT_SOURCE_DIR}/../app/src/main/cpp)
add_executable(leanmark_core_tests
    CoreTests.cpp
    ${CPP}/core/MarkdownCore.cpp
    ${CPP}/md4c/md4c.c
    ${CPP}/md4c/md4c-html.c
    ${CPP}/md4c/entity.c)
target_include_directories(leanmark_core_tests PRIVATE ${CPP} ${CPP}/md4c)
target_compile_features(leanmark_core_tests PRIVATE cxx_std_17)

enable_testing()
add_test(NAME leanmark-core COMMAND leanmark_core_tests)
