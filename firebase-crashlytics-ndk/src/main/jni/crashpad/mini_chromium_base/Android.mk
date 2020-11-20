LOCAL_PATH := $(call my-dir)

THIRD_PARTY_PATH := ../../../../third_party

include $(CLEAR_VARS)

LOCAL_MODULE := mini_chromium_base
LOCAL_C_INCLUDES := $(LOCAL_PATH)/$(THIRD_PARTY_PATH)/mini_chromium
LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)/$(THIRD_PARTY_PATH)/mini_chromium

LOCAL_CPPFLAGS := \
    -D_FILE_OFFSET_BITS=64 \
    -std=c++17 \
    -Wall \
    -Os \
    -flto \

LOCAL_SRC_FILES := \
    $(THIRD_PARTY_PATH)/mini_chromium/base/debug/alias.cc \
    $(THIRD_PARTY_PATH)/mini_chromium/base/files/file_path.cc \
    $(THIRD_PARTY_PATH)/mini_chromium/base/files/file_util_posix.cc \
    $(THIRD_PARTY_PATH)/mini_chromium/base/files/scoped_file.cc \
    $(THIRD_PARTY_PATH)/mini_chromium/base/logging.cc \
    $(THIRD_PARTY_PATH)/mini_chromium/base/posix/safe_strerror.cc \
    $(THIRD_PARTY_PATH)/mini_chromium/base/process/memory.cc \
    $(THIRD_PARTY_PATH)/mini_chromium/base/process/process_metrics_posix.cc \
    $(THIRD_PARTY_PATH)/mini_chromium/base/rand_util.cc \
    $(THIRD_PARTY_PATH)/mini_chromium/base/strings/string16.cc \
    $(THIRD_PARTY_PATH)/mini_chromium/base/strings/string_number_conversions.cc \
    $(THIRD_PARTY_PATH)/mini_chromium/base/strings/string_util.cc \
    $(THIRD_PARTY_PATH)/mini_chromium/base/strings/stringprintf.cc \
    $(THIRD_PARTY_PATH)/mini_chromium/base/strings/utf_string_conversion_utils.cc \
    $(THIRD_PARTY_PATH)/mini_chromium/base/strings/utf_string_conversions.cc \
    $(THIRD_PARTY_PATH)/mini_chromium/base/synchronization/condition_variable_posix.cc \
    $(THIRD_PARTY_PATH)/mini_chromium/base/synchronization/lock.cc \
    $(THIRD_PARTY_PATH)/mini_chromium/base/synchronization/lock_impl_posix.cc \
    $(THIRD_PARTY_PATH)/mini_chromium/base/third_party/icu/icu_utf.cc \
    $(THIRD_PARTY_PATH)/mini_chromium/base/threading/thread_local_storage.cc \
    $(THIRD_PARTY_PATH)/mini_chromium/base/threading/thread_local_storage_posix.cc \

include $(BUILD_STATIC_LIBRARY)
