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

#ifndef __CRASHLYTICS_CRASHPAD_HANDLER_MAIN_H__
#define __CRASHLYTICS_CRASHPAD_HANDLER_MAIN_H__

#include <jni.h>

extern "C" {

JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void* reserved);
JNIEXPORT jint JNI_Init(JNIEnv* env, jobject obj, jobjectArray file);

}

#endif // __CRASHLYTICS_CRASHPAD_HANDLER_MAIN_H__
