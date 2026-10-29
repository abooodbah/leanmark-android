cmake_minimum_required(VERSION 3.22.1)
project(leanmark C CXX)

# md4c and core/ are copied unchanged from the desktop repository; see
# third_party/README.md for the commit.
add_library(leanmark SHARED
    leanmark_jni.cpp
    core/MarkdownCore.cpp
    md4c/md4c.c
    md4c/md4c-html.c
    md4c/entity.c)

target_include_directories(leanmark PRIVATE ${CMAKE_CURRENT_SOURCE_DIR} md4c)
target_compile_features(leanmark PRIVATE cxx_std_17)
target_compile_options(leanmark PRIVATE
    -O2 -fvisibility=hidden -ffunction-sections -fdata-sections)
# Unused core functions (file and path helpers for desktop hosts) are dropped
# by --gc-sections. 16 KB pages are required by Google Play for apps that
# target Android 15 or later.
target_link_options(leanmark PRIVATE
    -Wl,--gc-sections -Wl,-z,max-page-size=16384)
