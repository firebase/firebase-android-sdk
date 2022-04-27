LOCAL_PATH := $(call my-dir)

THIRD_PARTY_PATH := ../../../../third_party

include $(CLEAR_VARS)

LOCAL_MODULE := crashpad_compat
LOCAL_C_INCLUDES := \
    $(LOCAL_PATH)/$(THIRD_PARTY_PATH)/crashpad \
    $(LOCAL_PATH)/$(THIRD_PARTY_PATH)/crashpad/compat/android \
    $(LOCAL_PATH)/$(THIRD_PARTY_PATH)/crashpad/compat/linux \
    $(LOCAL_PATH)/$(THIRD_PARTY_PATH)/crashpad/compat/non_mac \
    $(LOCAL_PATH)/$(THIRD_PARTY_PATH)/crashpad/compat/non_win \

LOCAL_EXPORT_C_INCLUDES := \
    $(LOCAL_PATH)/$(THIRD_PARTY_PATH)/crashpad/compat/android \
    $(LOCAL_PATH)/$(THIRD_PARTY_PATH)/crashpad/compat/linux \
    $(LOCAL_PATH)/$(THIRD_PARTY_PATH)/crashpad/compat/non_mac \
    $(LOCAL_PATH)/$(THIRD_PARTY_PATH)/crashpad/compat/non_win \

LOCAL_CPPFLAGS := \
    -D_FILE_OFFSET_BITS=64 \
    -Wall \
    -std=c++17 \
    -Os \
    -flto \
    -fvisibility=hidden \

LOCAL_SRC_FILES := \
    $(THIRD_PARTY_PATH)/crashpad/compat/android/dlfcn_internal.cc \
    $(THIRD_PARTY_PATH)/crashpad/compat/android/sys/epoll.cc \
    $(THIRD_PARTY_PATH)/crashpad/compat/android/sys/mman_mmap.cc \

LOCAL_STATIC_LIBRARIES := mini_chromium_base

include $(BUILD_STATIC_LIBRARY)
