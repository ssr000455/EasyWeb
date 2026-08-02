cmake_minimum_required(VERSION 3.18)
project(easyweb_js LANGUAGES C)

set(QUICKJS_VERSION "2024-01-13")
set(QUICKJS_DIR "${CMAKE_CURRENT_SOURCE_DIR}/quickjs-${QUICKJS_VERSION}")

# 如果 QuickJS 源码不存在，自动下载并解压
if(NOT EXISTS "${QUICKJS_DIR}/quickjs.h")
    message(STATUS "Downloading QuickJS ${QUICKJS_VERSION}...")
    file(DOWNLOAD
        "https://bellard.org/quickjs/quickjs-${QUICKJS_VERSION}.tar.gz"
        "${CMAKE_CURRENT_SOURCE_DIR}/quickjs.tar.gz"
        SHOW_PROGRESS
    )
    file(ARCHIVE_EXTRACT
        INPUT "${CMAKE_CURRENT_SOURCE_DIR}/quickjs.tar.gz"
        DESTINATION "${CMAKE_CURRENT_SOURCE_DIR}"
    )
    file(REMOVE "${CMAKE_CURRENT_SOURCE_DIR}/quickjs.tar.gz")
    message(STATUS "QuickJS downloaded and extracted.")
endif()

# QuickJS 源文件列表
set(QUICKJS_SOURCES
    ${QUICKJS_DIR}/quickjs.c
    ${QUICKJS_DIR}/quickjs-libc.c
    ${QUICKJS_DIR}/libunicode.c
    ${QUICKJS_DIR}/libregexp.c
    ${QUICKJS_DIR}/libbf.c
)

# QuickJS 头文件路径
include_directories(${QUICKJS_DIR})

# 编译 QuickJS 为静态库
add_library(quickjs STATIC ${QUICKJS_SOURCES})
target_compile_definitions(quickjs PRIVATE
    CONFIG_VERSION="${QUICKJS_VERSION}"
    _GNU_SOURCE
)
target_compile_options(quickjs PRIVATE -Wall -Wno-unused-function)

# 编译 JNI 桥接层，链接 QuickJS
add_library(js_engine SHARED
    jni_wrapper/js_engine.c
)
target_link_libraries(js_engine quickjs)
target_include_directories(js_engine PRIVATE ${QUICKJS_DIR})