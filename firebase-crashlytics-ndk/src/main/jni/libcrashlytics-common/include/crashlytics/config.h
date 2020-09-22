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

#ifndef __CRASHLYTICS_CONFIG_H__
#define __CRASHLYTICS_CONFIG_H__

//! Define this for internal testing.
//#define CRASHLYTICS_DEBUG

//! This should be defined for production builds. Undefining it removes the JNI specific entry points for
//  the purpose of being able to dynamically link with host JNI libraries.
#define CRASHLYTICS_INCLUDE_JNI_ENTRY

#include <system/log.h>

#if defined (CRASHLYTICS_DEBUG)
#    define DEBUG_OUT(...) LOGD(__VA_ARGS__)
#    define DEBUG_OUT_IF(cond, ...) { if ((cond)) { DEBUG_OUT(__VA_ARGS__); } }
#else
#    define DEBUG_OUT(...)
#    define DEBUG_OUT_IF(cond, ...)
#endif

#endif // __CRASHLYTICS_CONFIG_H__
