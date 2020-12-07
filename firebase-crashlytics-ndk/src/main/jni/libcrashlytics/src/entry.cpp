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

#include <cstddef>
#include <atomic>
#include <cerrno>
#include <string>
#include <unistd.h>
#include <sys/types.h>

#include "handler/handler_main.h"

#include "crashlytics/config.h"

#if defined (CRASHLYTICS_INCLUDE_JNI_ENTRY)
#    include <jni.h>
#endif

#include "crashlytics/entry.h"
#include "crashlytics/handler/install.h"
#include "crashlytics/handler/detail/context.h"
#include "crashlytics/detail/supplementary_file.h"
#include "system/log.h"

extern "C" {

extern int CrashpadHandlerMain(int argc, char* argv[]);

}

namespace google { namespace crashlytics { namespace entry {

#if defined (CRASHLYTICS_INCLUDE_JNI_ENTRY)

namespace jni {

const JNINativeMethod methods[] = {
    { "nativeInit", "([Ljava/lang/String;Ljava/lang/Object;)Z", reinterpret_cast<void *>(JNI_Init) }
};

//! We need to store the JVM environment to facilitate custom keys and logging, as they call back into
//  the JVM.
namespace detail { std::atomic<JavaVM *> jvm; }

JNIEnv* get_environment(JavaVM* jvm)
{
    JNIEnv* env;

    switch (jvm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6)) {
    case JNI_EDETACHED:
        LOGE("Failed to get the JVM environment; EDETACHED");
        break;
    case JNI_EVERSION:
        LOGE("Failed to get the JVM environment; EVERSION");
        break;
    case JNI_OK:
        detail::jvm.store(jvm);
        return env;
    }

    return NULL;
}

jclass find_class(JNIEnv* env, const char* path)
{
    return env->FindClass(path);
}

bool register_natives(const jclass& crashlytics_class, JNIEnv* env, const JNINativeMethod* methods, std::size_t methods_length)
{
    return env->RegisterNatives(crashlytics_class, methods, methods_length) == 0;
}

pid_t this_pid()
{
    return getpid();
}

const char* data_path(JNIEnv* env, jstring path)
{
    return env->GetStringUTFChars(path, NULL);
}

constexpr const char* ndk_path()
{
    return "com/google/firebase/crashlytics/ndk/JniNativeApi";
}

bool register_natives(JavaVM* jvm)
{
    if (JNIEnv* env = get_environment(jvm)) {
        if (jclass crashlytics_class = find_class(env, ndk_path())) {
            return register_natives(crashlytics_class, env, methods, sizeof methods / sizeof (methods[0]));
        }
    }

    DEBUG_OUT("Couldn't find %s and its necessary methods", ndk_path());
    return false;
}

} // namespace jni

#endif // CRASHLYTICS_INCLUDE_JNI_ENTRY

}}} // namepsace google::crashlytics::entry

#if defined (CRASHLYTICS_INCLUDE_JNI_ENTRY)

jint JNI_OnLoad(JavaVM* vm, void* reserved)
{
    return google::crashlytics::entry::jni::register_natives(vm) ? JNI_VERSION_1_6 : -1;
}

constexpr int path_ordinal() { return 2; }
constexpr int classpath_ordinal() { return 0; }
constexpr int libpath_ordinal() { return 1; }

jboolean JNI_Init(JNIEnv* env, jobject obj, jobjectArray pathsArray, jobject asset_manager)
{
    jstring path = static_cast<jstring>(
        env->GetObjectArrayElement(pathsArray, path_ordinal()));

    jstring classpath = static_cast<jstring>(
        env->GetObjectArrayElement(pathsArray,classpath_ordinal()));

    jstring lib_path = static_cast<jstring>(
        env->GetObjectArrayElement(pathsArray, libpath_ordinal()));

    bool installed = google::crashlytics::handler::install_handlers({
            google::crashlytics::entry::jni::this_pid(),
            google::crashlytics::entry::jni::data_path(env, path),
            env,
            env->GetStringUTFChars(classpath, 0),
            env->GetStringUTFChars(lib_path, 0)
    });

    LOGD("Initializing native crash handling %s.", installed ? "successful" : "failed");
    return installed;
}

#endif // CRASHLYTICS_INCLUDE_JNI_ENTRY
