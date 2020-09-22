LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

ifdef CRASHLYTICS_DEBUG
    LOCAL_CFLAGS += -DCRASHLYTICS_DEBUG
endif
LOCAL_CPPFLAGS := -std=c++11 -frtti -Wall
LOCAL_MODULE := crashlytics-common
LOCAL_LDLIBS := -llog -landroid
LOCAL_C_INCLUDES := $(LOCAL_PATH)/include

# Include all .cpp files in /src
rwildcard=$(foreach d,$(wildcard $1*),$(call rwildcard,$d/,$2) $(filter $(subst *,%,$2),$d))

SRC_FILE_LIST := $(call rwildcard, $(LOCAL_PATH)/src/, *.cpp)

LOCAL_SRC_FILES := $(SRC_FILE_LIST:$(LOCAL_PATH)/%=%)

include $(BUILD_SHARED_LIBRARY)