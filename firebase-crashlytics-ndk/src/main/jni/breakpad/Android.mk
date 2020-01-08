# Copyright 2019 Google LLC

# Include the main breakpad Android.mk after setting the LSS path.
THIRD_PARTY_PATH := $(call my-dir)/../../../third_party
include $(CLEAR_VARS)
LSS_PATH := $(THIRD_PARTY_PATH)/..
include $(THIRD_PARTY_PATH)/breakpad/android/google_breakpad/Android.mk
