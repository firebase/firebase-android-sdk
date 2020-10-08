LOCAL_PATH := $(call my-dir)

THIRD_PARTY_PATH := ../../../../third_party

include $(CLEAR_VARS)

LOCAL_MODULE := crashpad_handler_lib
LOCAL_C_INCLUDES := $(LOCAL_PATH)/$(THIRD_PARTY_PATH)/crashpad
LOCAL_CPPFLAGS := \
    -D_FILE_OFFSET_BITS=64 \
    -DCRASHPAD_ZLIB_SOURCE_SYSTEM \
    -Wall \
    -std=c++17 \
    -Os \
    -flto \
    -fvisibility=hidden \

LOCAL_SRC_FILES := \
    $(THIRD_PARTY_PATH)/crashpad/handler/crash_report_upload_thread.cc \
    $(THIRD_PARTY_PATH)/crashpad/handler/handler_main.cc \
    $(THIRD_PARTY_PATH)/crashpad/handler/linux/capture_snapshot.cc \
    $(THIRD_PARTY_PATH)/crashpad/handler/linux/crash_report_exception_handler.cc \
    $(THIRD_PARTY_PATH)/crashpad/handler/linux/exception_handler_server.cc \
    $(THIRD_PARTY_PATH)/crashpad/handler/minidump_to_upload_parameters.cc \
    $(THIRD_PARTY_PATH)/crashpad/handler/prune_crash_reports_thread.cc \
    $(THIRD_PARTY_PATH)/crashpad/handler/user_stream_data_source.cc \

LOCAL_STATIC_LIBRARIES := crashpad_compat mini_chromium_base

include $(BUILD_STATIC_LIBRARY)
