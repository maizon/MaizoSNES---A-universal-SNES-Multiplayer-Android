cmake_minimum_required(VERSION 3.22.1)
project(maizosnes LANGUAGES C CXX)

set(CMAKE_C_STANDARD 99)
set(CMAKE_C_STANDARD_REQUIRED ON)
set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

set(SNES9X_DIR "${CMAKE_CURRENT_SOURCE_DIR}/third_party/snes9x")
if(NOT EXISTS "${SNES9X_DIR}/libretro/Makefile.common" OR
   NOT EXISTS "${SNES9X_DIR}/libretro/libretro.cpp")
    message(FATAL_ERROR
        "Snes9x source tree missing. Run: bash scripts/fetch_snes9x.sh")
endif()

# Explicit list mirrors SOURCES_CXX from the pinned upstream libretro/Makefile.common.
set(SNES9X_SOURCES
    ${SNES9X_DIR}/apu/apu.cpp
    ${SNES9X_DIR}/apu/bapu/dsp/sdsp.cpp
    ${SNES9X_DIR}/apu/bapu/smp/smp.cpp
    ${SNES9X_DIR}/apu/bapu/smp/smp_state.cpp
    ${SNES9X_DIR}/bsx.cpp
    ${SNES9X_DIR}/c4.cpp
    ${SNES9X_DIR}/c4emu.cpp
    ${SNES9X_DIR}/cheats.cpp
    ${SNES9X_DIR}/cheats2.cpp
    ${SNES9X_DIR}/clip.cpp
    ${SNES9X_DIR}/conffile.cpp
    ${SNES9X_DIR}/controls.cpp
    ${SNES9X_DIR}/cpu.cpp
    ${SNES9X_DIR}/cpuexec.cpp
    ${SNES9X_DIR}/cpuops.cpp
    ${SNES9X_DIR}/crosshairs.cpp
    ${SNES9X_DIR}/dma.cpp
    ${SNES9X_DIR}/dsp.cpp
    ${SNES9X_DIR}/dsp1.cpp
    ${SNES9X_DIR}/dsp2.cpp
    ${SNES9X_DIR}/dsp3.cpp
    ${SNES9X_DIR}/dsp4.cpp
    ${SNES9X_DIR}/fxinst.cpp
    ${SNES9X_DIR}/fxemu.cpp
    ${SNES9X_DIR}/gfx.cpp
    ${SNES9X_DIR}/globals.cpp
    ${SNES9X_DIR}/memmap.cpp
    ${SNES9X_DIR}/obc1.cpp
    ${SNES9X_DIR}/msu1.cpp
    ${SNES9X_DIR}/ppu.cpp
    ${SNES9X_DIR}/stream.cpp
    ${SNES9X_DIR}/sa1.cpp
    ${SNES9X_DIR}/sa1cpu.cpp
    ${SNES9X_DIR}/screenshot.cpp
    ${SNES9X_DIR}/sdd1.cpp
    ${SNES9X_DIR}/sdd1emu.cpp
    ${SNES9X_DIR}/seta.cpp
    ${SNES9X_DIR}/seta010.cpp
    ${SNES9X_DIR}/seta011.cpp
    ${SNES9X_DIR}/seta018.cpp
    ${SNES9X_DIR}/snapshot.cpp
    ${SNES9X_DIR}/snes9x.cpp
    ${SNES9X_DIR}/spc7110.cpp
    ${SNES9X_DIR}/srtc.cpp
    ${SNES9X_DIR}/tile.cpp
    ${SNES9X_DIR}/tileimpl-n1x1.cpp
    ${SNES9X_DIR}/tileimpl-n2x1.cpp
    ${SNES9X_DIR}/tileimpl-h2x1.cpp
    ${SNES9X_DIR}/sha256.cpp
    ${SNES9X_DIR}/bml.cpp
    ${SNES9X_DIR}/movie.cpp
    ${SNES9X_DIR}/fscompat.cpp
    ${SNES9X_DIR}/libretro/libretro.cpp
)

# The official frontend's C source list contains the NTSC filter.
set(SNES9X_C_SOURCES
    ${SNES9X_DIR}/filter/snes_ntsc.c
)

add_library(snes9x_core STATIC ${SNES9X_SOURCES} ${SNES9X_C_SOURCES})
target_include_directories(snes9x_core PUBLIC
    ${SNES9X_DIR}
    ${SNES9X_DIR}/libretro
    ${SNES9X_DIR}/libretro/libretro-common/include
    ${SNES9X_DIR}/apu
    ${SNES9X_DIR}/apu/bapu
    ${SNES9X_DIR}/filter
)
target_compile_definitions(snes9x_core PRIVATE
    __LIBRETRO__
    ALLOW_CPU_OVERCLOCK
    HAVE_STDINT_H
    HAVE_STRINGS_H
    RIGHTSHIFT_IS_SAR
)
target_compile_options(snes9x_core PRIVATE -O2 -Wno-unused-parameter)
set_source_files_properties(${SNES9X_SOURCES} PROPERTIES
    COMPILE_OPTIONS "-fno-exceptions;-fno-rtti"
)

set(GEKKO_DIR "${CMAKE_CURRENT_SOURCE_DIR}/third_party/gekkonet")
if(NOT EXISTS "${GEKKO_DIR}/GekkoLib/include/gekkonet.h")
    message(FATAL_ERROR "GekkoNet missing: bash scripts/fetch_gekkonet.sh")
endif()
set(BUILD_DOCS OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(NO_ASIO_BUILD OFF CACHE BOOL "" FORCE)
add_subdirectory("${GEKKO_DIR}/GekkoLib" "${CMAKE_BINARY_DIR}/gekkonet")
add_library(gekko_bridge SHARED gekko_bridge.cpp)
target_link_libraries(gekko_bridge PRIVATE GekkoNet log)
target_compile_options(gekko_bridge PRIVATE -O2)
set_target_properties(gekko_bridge PROPERTIES CXX_STANDARD 20)

add_library(native-lib SHARED native-lib.cpp)
target_compile_options(native-lib PRIVATE -O2)
target_include_directories(native-lib PRIVATE
    ${SNES9X_DIR}/libretro
)
find_library(android_lib android)
find_library(log_lib log)
target_link_libraries(native-lib PRIVATE
    snes9x_core
    ${android_lib}
    ${log_lib}
)
