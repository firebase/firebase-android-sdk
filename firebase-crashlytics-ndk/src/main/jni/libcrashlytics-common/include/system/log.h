// Copyright 2019 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

/**
 * Macros for logging to the Android logger.
 */

// TODO: Look into expanding this logging to call back into the NDK Kit so it can call
// TODO: Fabric.getLogger() - discuss the right approach for this with the team.

#ifndef __CRASHLYTICS_SYSTEM_LOG_H__
#define __CRASHLYTICS_SYSTEM_LOG_H__

#include <android/log.h>

#define LOG_TAG "libcrashlytics"

#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

#endif // __CRASHLYTICS_SYSTEM_LOG_H__
