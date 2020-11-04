LOCAL_PATH := $(call my-dir)

THIRD_PARTY_PATH := ../../../third_party

include $(CLEAR_VARS)

ifdef CRASHLYTICS_DEBUG
    LOCAL_CFLAGS += -DCRASHLYTICS_DEBUG
endif

LOCAL_MODULE := crashlytics-common
LOCAL_C_INCLUDES := \
    $(LOCAL_PATH)/include \
    $(LOCAL_PATH)/$(THIRD_PARTY_PATH)/crashpad \

LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)/include
LOCAL_CPPFLAGS := \
    -std=c++17 \
    -Wall \
    -Os \
    -s \
    -ffunction-sections \
    -fdata-sections \
    -fno-stack-protector \
    -fomit-frame-pointer \
    -fno-unwind-tables \
    -fno-asynchronous-unwind-tables \
    -fno-unroll-loops \
    -fno-ident \
    -fno-exceptions \
    -fno-rtti \
    -fno-math-errno \
    -fmerge-all-constants \
    -fsingle-precision-constant \
    -ffast-math \

LOCAL_LDFLAGS := -flto -Wl,--gc-sections -Wl,--exclude-libs,ALL -Wl,-z,norelro
LOCAL_LDLIBS := -llog -lz -ldl

# Include all .cpp files in /src
rwildcard=$(foreach d,$(wildcard $1*),$(call rwildcard,$d/,$2) $(filter $(subst *,%,$2),$d))

SRC_FILE_LIST := $(call rwildcard, $(LOCAL_PATH)/src/, *.cpp)

LOCAL_SRC_FILES := $(SRC_FILE_LIST:$(LOCAL_PATH)/%=%)

LOCAL_STATIC_LIBRARIES := \
    crashpad_tool_support \
    crashpad_handler_lib \
    mini_chromium_base \
    crashpad_client \
    crashpad_compat \
    crashpad_util \
    crashpad_minidump \
    crashpad_snapshot \

include $(BUILD_SHARED_LIBRARY)
