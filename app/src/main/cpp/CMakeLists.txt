cmake_minimum_required(VERSION 3.22)
project(pitchpop_echo C)
set(CMAKE_C_STANDARD 99)
set(SPEEX ${CMAKE_CURRENT_SOURCE_DIR}/speexdsp)
add_library(pitchpop_echo SHARED echo_processor.c
    ${SPEEX}/libspeexdsp/mdf.c ${SPEEX}/libspeexdsp/preprocess.c
    ${SPEEX}/libspeexdsp/filterbank.c ${SPEEX}/libspeexdsp/fftwrap.c
    ${SPEEX}/libspeexdsp/smallft.c)
target_include_directories(pitchpop_echo PRIVATE ${SPEEX}/include ${SPEEX}/libspeexdsp ${CMAKE_CURRENT_SOURCE_DIR})
target_compile_definitions(pitchpop_echo PRIVATE FLOATING_POINT USE_SMALLFT EXPORT=)
target_link_libraries(pitchpop_echo m)
if(ANDROID)
    target_sources(pitchpop_echo PRIVATE echo_jni.c)
    # Android devices with 16 KB pages (also safe on 4 KB devices).
    target_link_options(pitchpop_echo PRIVATE "-Wl,-z,max-page-size=16384")
elseif(PITCHPOP_JNI_TESTS)
    find_package(JNI REQUIRED COMPONENTS JVM)
    target_sources(pitchpop_echo PRIVATE echo_jni.c)
    target_include_directories(pitchpop_echo PRIVATE ${JNI_INCLUDE_DIRS})
endif()
