LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

ifdef CRASHLYTICS_DEBUG
    LOCAL_CFLAGS += -DCRASHLYTICS_DEBUG
endif
LOCAL_CPPFLAGS := -std=c++11 -frtti -Wall
LOCAL_MODULE := crashlytics-trampoline
LOCAL_LDLIBS := -llog -landroid
LOCAL_C_INCLUDES := $(LOCAL_PATH)/include

LOCAL_SRC_FILES := $(LOCAL_PATH)/src/handler_trampoline.cpp

include $(BUILD_EXECUTABLE)
