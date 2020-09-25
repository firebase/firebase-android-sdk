LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE := crashpad-client
LOCAL_EXPORT_C_INCLUDES := \
	$(LOCAL_PATH)/../../../
LOCAL_SRC_FILES := \
	$(LOCAL_PATH)/../../../third_party/crashpad/crashpad/out/$(TARGET_ARCH_ABI)/out/Release/obj/client/libcrashpad_client.a
include $(PREBUILT_STATIC_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := crashpad-handler
LOCAL_SRC_FILES := \
	$(LOCAL_PATH)/../../../third_party/crashpad/crashpad/out/$(TARGET_ARCH_ABI)/out/Release/obj/handler/libcrashpad_handler_lib.a
include $(PREBUILT_STATIC_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := crashpad-util
LOCAL_EXPORT_C_INCLUDES := \
	$(LOCAL_PATH)/../../../third_party/crashpad/crashpad
LOCAL_SRC_FILES := \
	$(LOCAL_PATH)/../../../third_party/crashpad/crashpad/out/$(TARGET_ARCH_ABI)/out/Release/obj/util/libcrashpad_util.a
include $(PREBUILT_STATIC_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := base
LOCAL_EXPORT_C_INCLUDES := \
	$(LOCAL_PATH)/../../../third_party/crashpad/crashpad/third_party/mini_chromium/mini_chromium
LOCAL_SRC_FILES := \
	$(LOCAL_PATH)/../../../third_party/crashpad/crashpad/out/$(TARGET_ARCH_ABI)/out/Release/obj/third_party/mini_chromium/mini_chromium/base/libbase.a
include $(PREBUILT_STATIC_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := crashpad-tool-support
LOCAL_SRC_FILES := \
	$(LOCAL_PATH)/../../../third_party/crashpad/crashpad/out/$(TARGET_ARCH_ABI)/out/Release/obj/tools/libcrashpad_tool_support.a
include $(PREBUILT_STATIC_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := crashpad-snapshot
LOCAL_SRC_FILES := \
	$(LOCAL_PATH)/../../../third_party/crashpad/crashpad/out/$(TARGET_ARCH_ABI)/out/Release/obj/snapshot/libcrashpad_snapshot.a
include $(PREBUILT_STATIC_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := crashpad-minidump
LOCAL_SRC_FILES := \
	$(LOCAL_PATH)/../../../third_party/crashpad/crashpad/out/$(TARGET_ARCH_ABI)/out/Release/obj/minidump/libcrashpad_minidump.a
include $(PREBUILT_STATIC_LIBRARY)
