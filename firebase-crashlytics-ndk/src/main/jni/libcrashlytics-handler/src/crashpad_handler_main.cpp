// Copyright 2020 Google LLC
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

#include <jni.h>
#include <unistd.h>
#include <dlfcn.h>
#include <string>

#include "crashlytics/config.h"
#include "crashlytics/crashpad_handler_main.h"

extern "C" {

extern int CrashpadHandlerMain(int argc, char* argv[]);

}

namespace google { namespace crashlytics { namespace jni {

const JNINativeMethod methods[] = {
    { "crashpadMain", "([Ljava/lang/String;)V", reinterpret_cast<void *>(JNI_Init) }
};

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
        return env;
    }

    return nullptr;
}

jclass find_class(JNIEnv* env, const char* path)
{
    return env->FindClass(path);
}

bool register_natives(const jclass& crashlytics_class, JNIEnv* env, const JNINativeMethod* methods, size_t methods_length)
{
    return env->RegisterNatives(crashlytics_class, methods, methods_length) == 0;
}

constexpr const char* ndk_path()
{
    return "com/google/firebase/crashlytics/ndk/CrashpadMain";
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

namespace detail {

struct scoped_array_delete {
    scoped_array_delete(char** arr) : arr_(arr) {}
   ~scoped_array_delete() {
        delete [] arr_;
    }

private:
    char** arr_;
};

using CrashpadHandlerMainFunc = int (*)(int, char **);

} // namespace detail

detail::CrashpadHandlerMainFunc load_libcrashlytics_common(const std::string& lib_path)
{
    std::string full_path = lib_path + "libcrashlytics-common.so";

    void* common = dlopen(full_path.c_str(), RTLD_LAZY | RTLD_LOCAL);
    if (common == nullptr) {
        LOGE("Could not load libcrashlytics-common.so from %s", full_path.c_str());
        return nullptr;
    }

    void* handler = dlsym(common, "CrashpadHandlerMain");
    if (handler == nullptr) {
        LOGE("Could not find CrashpadHandlerMain in libcrashlytics-common.so");
        return nullptr;
    }

    return reinterpret_cast<detail::CrashpadHandlerMainFunc>(handler);
}

}}} // namespace google::crashlytics::jni

jint JNI_OnLoad(JavaVM* vm, void* reserved)
{
    return google::crashlytics::jni::register_natives(vm) ? JNI_VERSION_1_6 : -1;
}

jint JNI_Init(JNIEnv* env, jobject obj, jobjectArray pathsArray)
{
    jsize incoming_length = env->GetArrayLength(pathsArray);

    char** argv = new char*[incoming_length - 1];
    std::string lib_path;

    for (auto i = 0, j = 0; i < incoming_length; ++i) {
        jstring element =
            static_cast<jstring>(env->GetObjectArrayElement(pathsArray, i));

        if (i == 1) {
            lib_path = const_cast<char *>(env->GetStringUTFChars(element, 0));
            continue;
        }
        
        argv[j++] = const_cast<char *>(env->GetStringUTFChars(element, 0));
    }

    google::crashlytics::jni::detail::scoped_array_delete deletor{ argv };

    google::crashlytics::jni::detail::CrashpadHandlerMainFunc CrashpadHandlerMain =
        google::crashlytics::jni::load_libcrashlytics_common(lib_path);

    if (CrashpadHandlerMain != nullptr) {
        return CrashpadHandlerMain(incoming_length - 1, argv);
    }

    LOGE("Unable to load necessary components to capture crash");
    return 0;
}

int main(int argc, char* argv[])
{
    return CrashpadHandlerMain(argc, argv);
}
