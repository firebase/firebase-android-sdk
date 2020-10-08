LOCAL_PATH := $(call my-dir)

THIRD_PARTY_PATH := ../../../../third_party

include $(CLEAR_VARS)

LOCAL_MODULE := crashpad_client
LOCAL_C_INCLUDES := \
    $(LOCAL_PATH)/$(THIRD_PARTY_PATH)/.. \
    $(LOCAL_PATH)/$(THIRD_PARTY_PATH)/crashpad \

LOCAL_CPPFLAGS := \
    -D_FILE_OFFSET_BITS=64 \
    -DCRASHPAD_LSS_SOURCE_EXTERNAL \
    -Wall \
    -Os \
    -flto \
    -std=c++17 \

LOCAL_SRC_FILES := \
    $(THIRD_PARTY_PATH)/crashpad/client/annotation.cc \
    $(THIRD_PARTY_PATH)/crashpad/client/annotation_list.cc \
    $(THIRD_PARTY_PATH)/crashpad/client/crash_report_database.cc \
    $(THIRD_PARTY_PATH)/crashpad/client/crashpad_client_linux.cc \
    $(THIRD_PARTY_PATH)/crashpad/client/crashpad_info.cc \
    $(THIRD_PARTY_PATH)/crashpad/client/prune_crash_reports.cc \
    $(THIRD_PARTY_PATH)/crashpad/client/settings.cc \
    $(THIRD_PARTY_PATH)/crashpad/client/client_argv_handling.cc \
    $(THIRD_PARTY_PATH)/crashpad/client/crashpad_info_note.S \
    $(THIRD_PARTY_PATH)/crashpad/client/crash_report_database_generic.cc \

LOCAL_STATIC_LIBRARIES := crashpad_tool_support crashpad_util

include $(BUILD_STATIC_LIBRARY)
