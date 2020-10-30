LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

ifdef CRASHLYTICS_DEBUG
    LOCAL_CFLAGS += -DCRASHLYTICS_DEBUG
endif

LOCAL_MODULE := crashlytics-handler
LOCAL_C_INCLUDES := $(LOCAL_PATH)/include
LOCAL_CPPFLAGS := \
    -std=c++17 \
    -Wall \
    -Os \
    -s \
    -fvisibility=hidden \
    -nostdlib++ \

LOCAL_LDFLAGS := -Wl,--gc-sections -Wl,-z,norelro
LOCAL_LDLIBS := -llog -lz

# Include all .cpp files in /src
rwildcard=$(foreach d,$(wildcard $1*),$(call rwildcard,$d/,$2) $(filter $(subst *,%,$2),$d))

SRC_FILE_LIST := $(call rwildcard, $(LOCAL_PATH)/src/, *.cpp)

LOCAL_SRC_FILES := $(SRC_FILE_LIST:$(LOCAL_PATH)/%=%)

LOCAL_SHARED_LIBRARIES := crashlytics-common

include $(BUILD_SHARED_LIBRARY)
