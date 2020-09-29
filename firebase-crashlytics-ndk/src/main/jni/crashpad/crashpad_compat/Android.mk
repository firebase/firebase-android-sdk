LOCAL_PATH := $(call my-dir)

THIRD_PARTY_PATH := ../../../../third_party

include $(CLEAR_VARS)

LOCAL_MODULE := crashpad_compat

LOCAL_C_INCLUDES := $(LOCAL_PATH)/$(THIRD_PARTY_PATH)/crashpad \
                    $(LOCAL_PATH)/$(THIRD_PARTY_PATH)/crashpad/compat/android \
                    $(LOCAL_PATH)/$(THIRD_PARTY_PATH)/crashpad/compat/linux \
                    $(LOCAL_PATH)/$(THIRD_PARTY_PATH)/crashpad/compat/non_mac \
                    $(LOCAL_PATH)/$(THIRD_PARTY_PATH)/crashpad/compat/non_win

LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)/$(THIRD_PARTY_PATH)/crashpad/compat/android \
                           $(LOCAL_PATH)/$(THIRD_PARTY_PATH)/crashpad/compat/linux \
                           $(LOCAL_PATH)/$(THIRD_PARTY_PATH)/crashpad/compat/non_mac \
                           $(LOCAL_PATH)/$(THIRD_PARTY_PATH)/crashpad/compat/non_win

LOCAL_CFLAGS := -fPIC -fno-exceptions -fno-strict-aliasing -fstack-protector-all \
                -fvisibility=hidden -pipe -pthread -Wall -Werror -Wextra \
                -Wno-unused-parameter -Wno-missing-field-initializers -Wvla \
                -Wexit-time-destructors -Wextra-semi -Wheader-hygiene \
                -Wimplicit-fallthrough -Wsign-compare -Wstring-conversion -O3 \
                -fdata-sections -ffunction-sections

LOCAL_CPPFLAGS := -D_FILE_OFFSET_BITS=64 \
                  -fno-rtti -fvisibility-inlines-hidden -std=c++17

LOCAL_SRC_FILES := $(THIRD_PARTY_PATH)/crashpad/compat/android/dlfcn_internal.cc \
                   $(THIRD_PARTY_PATH)/crashpad/compat/android/sys/epoll.cc \
                   $(THIRD_PARTY_PATH)/crashpad/compat/android/sys/mman_mmap.cc

LOCAL_STATIC_LIBRARIES := mini_chromium_base

include $(BUILD_STATIC_LIBRARY)
