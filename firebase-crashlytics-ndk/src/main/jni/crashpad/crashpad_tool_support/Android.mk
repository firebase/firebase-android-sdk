LOCAL_PATH := $(call my-dir)

THIRD_PARTY_PATH := ../../../../third_party

include $(CLEAR_VARS)

LOCAL_MODULE := crashpad_tool_support

LOCAL_C_INCLUDES := $(LOCAL_PATH)/$(THIRD_PARTY_PATH)/crashpad

LOCAL_CFLAGS := -fPIC -fno-exceptions -fno-strict-aliasing -fstack-protector-all \
                -fvisibility=hidden -pipe -pthread -Wall -Werror -Wextra \
                -Wno-unused-parameter -Wno-missing-field-initializers -Wvla \
                -Wexit-time-destructors -Wextra-semi -Wheader-hygiene \
                -Wimplicit-fallthrough -Wsign-compare -Wstring-conversion -O3 \
                -fdata-sections -ffunction-sections

LOCAL_CPPFLAGS := -D_FILE_OFFSET_BITS=64 \
                  -fno-rtti -fvisibility-inlines-hidden -std=c++17

LOCAL_SRC_FILES := $(THIRD_PARTY_PATH)/crashpad/tools/tool_support.cc

LOCAL_STATIC_LIBRARIES := mini_chromium_base

include $(BUILD_STATIC_LIBRARY)
