LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

ifdef CRASHLYTICS_DEBUG
    LOCAL_CFLAGS += -DCRASHLYTICS_DEBUG
endif

LOCAL_MODULE := crashlytics-trampoline
LOCAL_C_INCLUDES := $(LOCAL_PATH)/include
LOCAL_CPPFLAGS := \
    -std=c++17 \
    -Wall \
    -Os \
    -s \
    -fvisibility=hidden \
    -ffunction-sections \
    -fdata-sections \
    -fno-stack-protector \
    -fomit-frame-pointer \
    -fno-unwind-tables \
    -fno-asynchronous-unwind-tables \
    -fno-unroll-loops \
    -fno-ident \

LOCAL_LDFLAGS := -Wl,--gc-sections -Wl,-z,norelro
LOCAL_LDLIBS := -llog

LOCAL_SRC_FILES := $(LOCAL_PATH)/src/handler_trampoline.cpp

include $(BUILD_EXECUTABLE)
