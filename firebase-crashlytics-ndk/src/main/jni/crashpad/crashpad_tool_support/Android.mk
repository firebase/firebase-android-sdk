LOCAL_PATH := $(call my-dir)

THIRD_PARTY_PATH := ../../../../third_party

include $(CLEAR_VARS)

LOCAL_MODULE := crashpad_tool_support
LOCAL_C_INCLUDES := $(LOCAL_PATH)/$(THIRD_PARTY_PATH)/crashpad

LOCAL_CPPFLAGS := \
    -D_FILE_OFFSET_BITS=64 \
    -std=c++17 \
    -Wall \
    -Os \
    -flto \
    -fvisibility=hidden \

LOCAL_SRC_FILES := $(THIRD_PARTY_PATH)/crashpad/tools/tool_support.cc

LOCAL_STATIC_LIBRARIES := mini_chromium_base

include $(BUILD_STATIC_LIBRARY)
