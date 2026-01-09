cmake_minimum_required(VERSION 3.18.1)

project(Yuri)

set(SOURCE_PATH ${CMAKE_CURRENT_SOURCE_DIR})
set(CMAKE_C_VISIBILITY_PRESET hidden)
set(CMAKE_CXX_VISIBILITY_PRESET hidden)

message("Build type: ${CMAKE_BUILD_TYPE}")

set(CMAKE_CXX_STANDARD 20)

set(COMMON_FLAGS "-ffixed-x18 -fdata-sections -ffunction-sections -fvisibility=hidden")
set(C_FLAGS "${COMMON_FLAGS} -Werror=format")
set(CXX_FLAGS "${COMMON_FLAGS} -fno-exceptions -fno-rtti -Wno-error=format-security -Werror -Wno-error=c++11-narrowing -fpermissive -Wall")

set(LINKER_FLAGS "-Wl,--hash-style=both -Wl,-Bsymbolic")

if (NOT CMAKE_BUILD_TYPE STREQUAL "Debug")
    set(OPTIMIZATION_FLAGS "-O2 -fvisibility-inlines-hidden")
    set(C_FLAGS "${C_FLAGS} ${OPTIMIZATION_FLAGS}")
    set(CXX_FLAGS "${CXX_FLAGS} ${OPTIMIZATION_FLAGS}")
    set(LINKER_FLAGS "${LINKER_FLAGS} -Wl,-exclude-libs,ALL -Wl,--gc-sections -s")
else ()
    set(C_FLAGS "${C_FLAGS} -O0")
    set(CXX_FLAGS "${CXX_FLAGS} -O0")
endif ()

set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} ${C_FLAGS}")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} ${CXX_FLAGS}")
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} ${LINKER_FLAGS}")
set(CMAKE_MODULE_LINKER_FLAGS "${CMAKE_MODULE_LINKER_FLAGS} ${LINKER_FLAGS}")

include_directories(${SOURCE_PATH})
include_directories(include)

aux_source_directory(xDL xdl-src)

add_library(${PROJECT_NAME} SHARED 
        main.cpp
        ${xdl-src}
        )

set(DOBBY_LIB ${CMAKE_CURRENT_SOURCE_DIR}/Dobby/${CMAKE_ANDROID_ARCH_ABI}/libdobby.a)

target_link_libraries(${PROJECT_NAME} log android ${DOBBY_LIB})

if (NOT CMAKE_BUILD_TYPE STREQUAL "Debug")
    add_custom_command(TARGET ${PROJECT_NAME} POST_BUILD
            COMMAND ${CMAKE_STRIP} --strip-all --remove-section=.comment "$<TARGET_FILE:${PROJECT_NAME}>"
            COMMENT "Stripping symbols from ${PROJECT_NAME}"
            VERBATIM)
endif ()