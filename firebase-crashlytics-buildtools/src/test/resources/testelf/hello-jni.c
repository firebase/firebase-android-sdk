/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
#include <string.h>
#include <jni.h>
#include <android/log.h>

// JNI example adapted from AOSP "Hello-JNI", extended to include inline functions

inline __attribute__((always_inline))
jstring inlinedFunctionThatReturns( JNIEnv* env )
{
#if defined(__arm__)
    #if defined(__ARM_ARCH_7A__)
    #if defined(__ARM_NEON__)
      #if defined(__ARM_PCS_VFP)
        #define ABI "armeabi-v7a/NEON (hard-float)"
      #else
        #define ABI "armeabi-v7a/NEON"
      #endif
    #else
      #if defined(__ARM_PCS_VFP)
        #define ABI "armeabi-v7a (hard-float)"
      #else
        #define ABI "armeabi-v7a"
      #endif
    #endif
  #else
   #define ABI "armeabi"
  #endif
#elif defined(__i386__)
#define ABI "x86"
#elif defined(__x86_64__)
#define ABI "x86_64"
#elif defined(__mips64)  /* mips64el-* toolchain defines __mips__ too */
#define ABI "mips64"
#elif defined(__mips__)
#define ABI "mips"
#elif defined(__aarch64__)
#define ABI "arm64-v8a"
#else
#define ABI "unknown"
#endif

    return (*env)->NewStringUTF(env, "Hello from JNI !  Compiled with ABI " ABI ".");
}

inline __attribute__((always_inline))
void inlinedSingleLineFunction() {
    __android_log_print(ANDROID_LOG_DEBUG, "TAG", "Inlined Single-line function.");
}

inline __attribute__((always_inline))
void inlinedMultiLineFunction() {
    __android_log_print(ANDROID_LOG_DEBUG, "TAG", "Inlined Multi-line function, 1st line.");
    // adding a comment here for FUN!
    __android_log_print(ANDROID_LOG_DEBUG, "TAG", "Inlined Multi-line function, 2nd line.");
}

inline __attribute__((always_inline))
void inlinedFunctionWithNestedInlinedCall() {
    __android_log_print(ANDROID_LOG_DEBUG, "TAG", "Inlined function about to call another inlined function:");
    inlinedMultiLineFunction();
    __android_log_print(ANDROID_LOG_DEBUG, "TAG", "Inlined function that just called another inlined function.");
}

void notInlinedFunction() {
    __android_log_print(ANDROID_LOG_DEBUG, "TAG", "Not an inlined function");
}

/* This is a trivial JNI example where we use a native method
 * to return a new VM String. See the corresponding Java source
 * file located at:
 *
 *   hello-jni/app/src/main/java/com/example/hellojni/HelloJni.java
 */
JNIEXPORT jstring JNICALL
Java_com_example_hellojni_HelloJni_stringFromJNI( JNIEnv* env,
                                                  jobject thiz )
{
    notInlinedFunction();
    inlinedSingleLineFunction();
    notInlinedFunction();
    inlinedMultiLineFunction();
    notInlinedFunction();
    inlinedFunctionWithNestedInlinedCall();
    notInlinedFunction();
    return inlinedFunctionThatReturns(env);
}




inline __attribute__((always_inline))
int forceCrashInline(int value) {
    __android_log_print(ANDROID_LOG_DEBUG, "TAG", "About to crash in an inlined function...");

    int *x = NULL;
    *x = value;

    return *x + value;
}

__attribute__((noinline))
int forceCrash(int value) {
    __android_log_print(ANDROID_LOG_DEBUG, "TAG", "About to crash in a non-inlined function...");

    int *x = NULL;
    *x = value;

    return *x + value;
}

JNIEXPORT jstring JNICALL
Java_com_example_hellojni_HelloJni_jniCrasher( JNIEnv* env,
                                               jobject thiz )
{
    __android_log_print(ANDROID_LOG_DEBUG, "TAG", "This is the JNI call...");
    forceCrash(10);
    return (*env)->NewStringUTF(env, "NOPE");
}

JNIEXPORT jstring JNICALL
Java_com_example_hellojni_HelloJni_jniInlineCrasher( JNIEnv* env,
                                               jobject thiz )
{
    forceCrashInline(100);
    return (*env)->NewStringUTF(env, "NOPE");
}
