cmake_minimum_required(VERSION 3.22)
project(grayjay_adb LANGUAGES C CXX ASM)

if(NOT ANDROID)
    message(FATAL_ERROR "This build requires the Android NDK toolchain")
endif()
if(NOT AOSP_SOURCES OR NOT PROTOC)
    message(FATAL_ERROR "Set AOSP_SOURCES and PROTOC to the pinned source tree and host compiler")
endif()

set(CMAKE_CXX_STANDARD 23)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
add_compile_options(-ffunction-sections -fdata-sections
    "-ffile-prefix-map=${AOSP_SOURCES}=aosp"
    "-ffile-prefix-map=${CMAKE_CURRENT_SOURCE_DIR}=grayjay/native/adb"
    "-ffile-prefix-map=${CMAKE_CURRENT_BINARY_DIR}=build")
add_compile_definitions(__ANDROID_UNAVAILABLE_SYMBOLS_ARE_WEAK__)
add_link_options(-Wl,--gc-sections -Wl,-z,max-page-size=16384)

set(ADB "${AOSP_SOURCES}/adb")
set(CORE "${AOSP_SOURCES}/core")
set(GENERATED "${CMAKE_CURRENT_BINARY_DIR}/generated")
file(MAKE_DIRECTORY "${GENERATED}/build" "${GENERATED}/fastdeploy/proto")
file(WRITE "${GENERATED}/platform_tools_version.h" "#define PLATFORM_TOOLS_VERSION \"android-16.0.0_r1-grayjay\"\n")
file(WRITE "${GENERATED}/build/version.h"
    "#pragma once\n#include <string>\nnamespace android::build { inline std::string GetBuildNumber() { return \"aosp-ce023afef190\"; } }\n")

set(protobuf_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(protobuf_BUILD_PROTOC_BINARIES OFF CACHE BOOL "" FORCE)
set(protobuf_WITH_ZLIB OFF CACHE BOOL "" FORCE)
add_subdirectory("${AOSP_SOURCES}/protobuf" protobuf EXCLUDE_FROM_ALL)
target_include_directories(libprotobuf PRIVATE "${AOSP_SOURCES}/protobuf/android")
add_subdirectory("${AOSP_SOURCES}/boringssl" boringssl EXCLUDE_FROM_ALL)
set_target_properties(crypto ssl PROPERTIES CXX_STANDARD 17)
set(FMT_TEST OFF CACHE BOOL "" FORCE)
set(FMT_DOC OFF CACHE BOOL "" FORCE)
set(FMT_INSTALL OFF CACHE BOOL "" FORCE)
add_subdirectory("${AOSP_SOURCES}/fmtlib" fmtlib EXCLUDE_FROM_ALL)

set(PROTO_SOURCES)
foreach(PROTO adb_host adb_known_hosts app_processes key_type pairing)
    add_custom_command(
        OUTPUT "${GENERATED}/${PROTO}.pb.cc" "${GENERATED}/${PROTO}.pb.h"
        COMMAND "${PROTOC}" "--proto_path=${ADB}/proto" "--cpp_out=${GENERATED}"
            "${ADB}/proto/${PROTO}.proto"
        DEPENDS "${ADB}/proto/${PROTO}.proto"
        VERBATIM)
    list(APPEND PROTO_SOURCES "${GENERATED}/${PROTO}.pb.cc")
endforeach()
add_custom_command(
    OUTPUT "${GENERATED}/fastdeploy/proto/ApkEntry.pb.cc" "${GENERATED}/fastdeploy/proto/ApkEntry.pb.h"
    COMMAND "${PROTOC}" "--proto_path=${ADB}" "--cpp_out=${GENERATED}"
        "${ADB}/fastdeploy/proto/ApkEntry.proto"
    DEPENDS "${ADB}/fastdeploy/proto/ApkEntry.proto"
    VERBATIM)
add_library(adb_protos STATIC ${PROTO_SOURCES} "${GENERATED}/fastdeploy/proto/ApkEntry.pb.cc")
target_include_directories(adb_protos PUBLIC "${GENERATED}")
target_link_libraries(adb_protos PUBLIC libprotobuf)

set(BASE_SOURCES
    abi_compatibility.cpp chrono_utils.cpp cmsg.cpp errors_unix.cpp file.cpp hex.cpp
    logging.cpp mapped_file.cpp parsebool.cpp parsenetaddress.cpp posix_strerror_r.cpp
    process.cpp result.cpp stringprintf.cpp strings.cpp threads.cpp)
list(TRANSFORM BASE_SOURCES PREPEND "${AOSP_SOURCES}/libbase/")
add_library(adb_base STATIC ${BASE_SOURCES})
target_include_directories(adb_base PUBLIC "${AOSP_SOURCES}/libbase/include")
target_link_libraries(adb_base PUBLIC fmt log)

set(SOCKET_SOURCES
    sockets.cpp sockets_unix.cpp socket_local_client_unix.cpp socket_local_server_unix.cpp
    socket_network_client_unix.cpp socket_inaddr_any_server_unix.cpp)
list(TRANSFORM SOCKET_SOURCES PREPEND "${CORE}/libcutils/")
add_library(adb_sockets STATIC ${SOCKET_SOURCES})
target_include_directories(adb_sockets PUBLIC "${CORE}/libcutils/include")

file(GLOB BROTLI_SOURCES
    "${AOSP_SOURCES}/brotli/c/common/*.c"
    "${AOSP_SOURCES}/brotli/c/enc/*.c"
    "${AOSP_SOURCES}/brotli/c/dec/*.c")
add_library(adb_brotli STATIC ${BROTLI_SOURCES})
target_include_directories(adb_brotli PUBLIC "${AOSP_SOURCES}/brotli/c/include")
add_library(adb_lz4 STATIC "${AOSP_SOURCES}/lz4/lib/lz4.c" "${AOSP_SOURCES}/lz4/lib/lz4hc.c"
    "${AOSP_SOURCES}/lz4/lib/lz4frame.c" "${AOSP_SOURCES}/lz4/lib/xxhash.c")
target_include_directories(adb_lz4 PUBLIC "${AOSP_SOURCES}/lz4/lib")
set(ZSTD_BUILD_PROGRAMS OFF CACHE BOOL "" FORCE)
set(ZSTD_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(ZSTD_BUILD_SHARED OFF CACHE BOOL "" FORCE)
add_subdirectory("${AOSP_SOURCES}/zstd/build/cmake" zstd EXCLUDE_FROM_ALL)

set(ADB_SOURCES
    adb.cpp adb_io.cpp adb_listeners.cpp adb_mdns.cpp adb_trace.cpp adb_unique_fd.cpp
    adb_utils.cpp apacket_reader.cpp services.cpp sockets.cpp socket_spec.cpp
    sysdeps/env.cpp sysdeps/errno.cpp sysdeps/posix/network.cpp sysdeps_unix.cpp
    transport.cpp transport_fd.cpp types.cpp fdevent/fdevent.cpp fdevent/fdevent_epoll.cpp
    client/auth.cpp client/adb_wifi.cpp client/detach.cpp client/transport_emulator.cpp
    client/transport_usb.cpp client/mdns_utils.cpp client/pairing/pairing_client.cpp
    client/adb_client.cpp client/bugreport.cpp client/commandline.cpp client/file_sync_client.cpp
    client/main.cpp client/console.cpp client/adb_install.cpp client/line_printer.cpp
    shell_service_protocol.cpp crypto/key.cpp crypto/rsa_2048_key.cpp crypto/x509_generator.cpp
    pairing_auth/aes_128_gcm.cpp pairing_auth/pairing_auth.cpp
    pairing_connection/pairing_connection.cpp tls/adb_ca_list.cpp tls/tls_connection.cpp)
list(TRANSFORM ADB_SOURCES PREPEND "${ADB}/")
add_executable(grayjay_adb ${ADB_SOURCES} android_backends.cpp
    "${CORE}/libcrypto_utils/android_pubkey.cpp" "${CORE}/diagnose_usb/diagnose_usb.cpp")
set_target_properties(grayjay_adb PROPERTIES OUTPUT_NAME libgrayjay_adb.so)
target_compile_definitions(grayjay_adb PRIVATE ADB_HOST=1
    ANDROID_BASE_UNIQUE_FD_DISABLE_IMPLICIT_CONVERSION=1)
target_include_directories(grayjay_adb PRIVATE
    "${ADB}" "${ADB}/client" "${ADB}/crypto/include" "${ADB}/pairing_auth/include"
    "${ADB}/pairing_connection/include" "${ADB}/tls/include"
    "${CORE}/libcrypto_utils/include" "${CORE}/diagnose_usb/include"
    "${AOSP_SOURCES}/boringssl/src/include" "${AOSP_SOURCES}/zstd/lib")
target_link_libraries(grayjay_adb PRIVATE adb_protos adb_base adb_sockets
    adb_brotli adb_lz4 libzstd_static ssl crypto log)
