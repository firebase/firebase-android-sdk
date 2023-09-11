/*
 * Copyright 2023 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include <jni.h>
#include <string>
#include<unistd.h>

extern "C" JNIEXPORT jstring JNICALL
Java_com_google_firebase_testing_config_MainActivity_stringFromJNI(
        JNIEnv *env,
        jobject /* this */) {
    std::string hello = "Hello from C++";
    return env->NewStringUTF(hello.c_str());
}

extern "C"
JNIEXPORT void JNICALL
Java_com_google_firebase_testing_config_MainActivity_nativeCrash(
        JNIEnv *env,
        jobject /* this */) {
    int *i = nullptr;
    *i = 7;
}
extern "C"
JNIEXPORT void JNICALL
Java_com_google_firebase_testing_config_MainActivity_nativeAnr(
        JNIEnv *env,
        jobject /* this */) {
    for (;;) {
        sleep(1);
    }
}
