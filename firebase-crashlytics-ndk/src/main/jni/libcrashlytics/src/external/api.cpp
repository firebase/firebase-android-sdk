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

#include "crashlytics/config.h"

#if defined (CRASHLYTICS_DEBUG)
#    include "crashlytics/external/crashlytics.h"
#endif

#include <jni.h>
#include <atomic>
#include <new>
#include <initializer_list>

namespace google { namespace crashlytics { namespace entry { namespace jni { namespace detail {

extern std::atomic<JavaVM *> jvm;

struct managed_environment {
    managed_environment(JavaVM* jvm);
   ~managed_environment();

    JNIEnv* get() const;
private:
    JavaVM* jvm_;
    JNIEnv* environment_;
    bool    attached_;
};

managed_environment::managed_environment(JavaVM* jvm)
    : jvm_(jvm), environment_(NULL), attached_(false)
{
    if (jvm_ == NULL) {
        return;
    }

    switch (jvm_->GetEnv(reinterpret_cast<void **>(&environment_), JNI_VERSION_1_6)) {
    case JNI_EDETACHED:
        DEBUG_OUT("Calling JNI method from a non JVM thread, attaching...");
        if (jvm_->AttachCurrentThread(&environment_, NULL) != 0) {
            DEBUG_OUT("Failed to attach!");
            attached_ = false;
            break;
        }
        DEBUG_OUT("Attached successfully!");
        attached_ = true;
    case JNI_OK:
        return;
    }
}

managed_environment::~managed_environment()
{
    if (attached_) {
        jvm_->DetachCurrentThread();
    }
}

JNIEnv* managed_environment::get() const
{
    return environment_;
}

}}}}}

namespace google { namespace crashlytics { namespace api { namespace detail {

struct jvm_context {
public:
    jvm_context(
            jobject     crashlytics,
            jmethodID   log,
            jmethodID   set,
            jmethodID   set_user_id
    );

public:
    jobject        crashlytics_;
    jmethodID      log_;
    jmethodID      set_;
    jmethodID      set_user_id_;
};

}}}}

//! Implementation

google::crashlytics::api::detail::jvm_context::jvm_context(
        jobject     crashlytics,
        jmethodID   log,
        jmethodID   set,
        jmethodID   set_user_id
)
    : crashlytics_   (crashlytics),
      log_                (log),
      set_                (set),
      set_user_id_(set_user_id)
{
}

extern "C" {

google::crashlytics::api::detail::jvm_context* external_api_initialize()                        __attribute__((visibility ("default")));
void external_api_dispose(
    google::crashlytics::api::detail::jvm_context* context)                                     __attribute__((visibility ("default")));
void external_api_set(
    google::crashlytics::api::detail::jvm_context* context, const char* key, const char* value) __attribute__((visibility ("default")));
void external_api_log(
    google::crashlytics::api::detail::jvm_context* context, const char* message)                __attribute__((visibility ("default")));
void external_api_set_user_id(
    google::crashlytics::api::detail::jvm_context* context, const char* identifier)             __attribute__((visibility ("default")));

}

google::crashlytics::api::detail::jvm_context* external_api_initialize()
{
    DEBUG_OUT("Initializing API context...");

    jclass     crashlytics;
    jclass     crashlytics_global;
    jmethodID  crashlytics_log;
    jmethodID  crashlytics_set;
    jmethodID  crashlytics_set_user_id;
    jmethodID  crashlytics_get_instance;
    jobject    crashlytics_instance;
    jobject    crashlytics_instance_global;
    JNIEnv*    environment;

    google::crashlytics::entry::jni::detail::managed_environment env(
        google::crashlytics::entry::jni::detail::jvm.load()
    );

    if ((environment = env.get()) == NULL) {
        DEBUG_OUT("Global environment not set.");
        return nullptr;
    }

    if ((crashlytics = environment->FindClass("com/google/firebase/crashlytics/FirebaseCrashlytics")) == NULL) {
        DEBUG_OUT("Couldn't find com/google/firebase/crashlytics/FirebaseCrashlytics");
        return nullptr;
    }

    if ((crashlytics_global = static_cast<jclass>(environment->NewGlobalRef(crashlytics))) == NULL) {
        DEBUG_OUT("Couldn't create a new global reference for FirebaseCrashlytics.class");
        return nullptr;
    }

    if ((crashlytics_log = environment->GetMethodID(crashlytics_global, "log", "(Ljava/lang/String;)V")) == NULL) {
        DEBUG_OUT("Couldn't find method 'FirebaseCrashlytics.log'");
        return nullptr;
    }

    if ((crashlytics_set = environment->GetMethodID(crashlytics_global, "setCustomKey", "(Ljava/lang/String;Ljava/lang/String;)V")) == NULL) {
        DEBUG_OUT("Couldn't find method 'FirebaseCrashlytics.setString'");
        return nullptr;
    }

    if ((crashlytics_set_user_id = environment->GetMethodID(crashlytics_global, "setUserId", "(Ljava/lang/String;)V")) == NULL) {
        DEBUG_OUT("Couldn't find method 'FirebaseCrashlytics.setUserId'");
        return nullptr;
    }

    if ((crashlytics_get_instance = environment->GetStaticMethodID(crashlytics_global, "getInstance", "()Lcom/google/firebase/crashlytics/FirebaseCrashlytics;")) == NULL) {
        DEBUG_OUT("Couldn't find method 'FirebaseCrashlytics.getInstance'");
        return nullptr;
    }

    if ((crashlytics_instance = environment->CallStaticObjectMethod(crashlytics, crashlytics_get_instance)) == NULL) {
        DEBUG_OUT("Couldn't invoke 'FirebaseCrashlytics.getInstance'");
        return nullptr;
    }

    if ((crashlytics_instance_global = environment->NewGlobalRef(crashlytics_instance)) == NULL) {
        DEBUG_OUT("Couldn't create a new global reference for an instance of FirebaseCrashlytics");
        return nullptr;
    }

    DEBUG_OUT("Done.");
    return new (std::nothrow) google::crashlytics::api::detail::jvm_context(
            crashlytics_instance_global,
            crashlytics_log,
            crashlytics_set,
            crashlytics_set_user_id
    );
}

void external_api_dispose(google::crashlytics::api::detail::jvm_context* context)
{
    DEBUG_OUT("Finalizing API context");
    delete context;
}

namespace google { namespace crashlytics { namespace detail {

inline bool null_context(google::crashlytics::api::detail::jvm_context* context)
{
    return  context                    == NULL ||
            context->crashlytics_      == NULL ||
            context->log_              == NULL ||
            context->set_              == NULL ||
            context->set_user_id_      == NULL;
}

struct managed_jstring {
    managed_jstring(JNIEnv* environment, const char* str);
   ~managed_jstring();

    jstring get() const;
    operator bool() const;
private:
    JNIEnv* environment_;
    jstring str_;
};

}}}

google::crashlytics::detail::managed_jstring::managed_jstring(JNIEnv* environment, const char* str)
    : environment_(environment)
{
    str_ = environment_->NewStringUTF(str);
}

google::crashlytics::detail::managed_jstring::~managed_jstring()
{
    if (str_ != NULL) {
        environment_->DeleteLocalRef(str_);
    }
}

inline jstring google::crashlytics::detail::managed_jstring::get() const
{
    return str_;
}

inline google::crashlytics::detail::managed_jstring::operator bool() const
{
    return str_ != NULL;
}

namespace google { namespace crashlytics { namespace detail {

void invoke1(JNIEnv* environment, jobject crashlytics_core, jmethodID method, const char* arg1)
{
    google::crashlytics::detail::managed_jstring marshalled1(environment, arg1);

    if (!marshalled1) {
        DEBUG_OUT("Couldn't allocate a new marshalled string in %s", __PRETTY_FUNCTION__);
        return;
    }

    environment->CallVoidMethod(crashlytics_core, method, marshalled1.get());
}

void invoke2(JNIEnv* environment, jobject crashlytics_core, jmethodID method, const char* arg1, const char* arg2)
{
    google::crashlytics::detail::managed_jstring marshalled1(environment, arg1);
    google::crashlytics::detail::managed_jstring marshalled2(environment, arg2);

    if (!marshalled1 || !marshalled2) {
        DEBUG_OUT("Couldn't allocate a new marshalled string in %s", __PRETTY_FUNCTION__);
        return;
    }

    environment->CallVoidMethod(crashlytics_core, method, marshalled1.get(), marshalled2.get());
}

void invokeN(JNIEnv* environment, jobject crashlytics_core, jmethodID method, std::initializer_list<const char *> arguments)
{
    if (environment == NULL || crashlytics_core == NULL) {
        DEBUG_OUT("Failed to invoke method due to environmental issues");
        return;
    }

    switch (arguments.size()) {
    case 1:
        invoke1(environment, crashlytics_core, method, *arguments.begin());
        break;
    case 2:
        invoke2(environment, crashlytics_core, method, *arguments.begin(), *(arguments.begin() + 1));
        break;
    }
}

}}}

void external_api_set(google::crashlytics::api::detail::jvm_context* context, const char* key, const char* value)
{
    if (google::crashlytics::detail::null_context(context) || key == NULL || value == NULL) {
        DEBUG_OUT("Context and arguments can't be NULL");
        return;
    }

    DEBUG_OUT("set: %s = %s", key, value);
    google::crashlytics::entry::jni::detail::managed_environment env(google::crashlytics::entry::jni::detail::jvm.load());
    google::crashlytics::detail::invokeN(env.get(), context->crashlytics_, context->set_, { key, value });
}

void external_api_log(google::crashlytics::api::detail::jvm_context* context, const char* message)
{
    if (google::crashlytics::detail::null_context(context) || message == NULL) {
        DEBUG_OUT("Context and argument can't be NULL");
        return;
    }

    DEBUG_OUT("log: %s", message);
    google::crashlytics::entry::jni::detail::managed_environment env(google::crashlytics::entry::jni::detail::jvm.load());
    google::crashlytics::detail::invokeN(env.get(), context->crashlytics_, context->log_, { message });
}

void external_api_set_user_id(google::crashlytics::api::detail::jvm_context* context, const char* identifier)
{
    if (google::crashlytics::detail::null_context(context) || identifier == NULL) {
        DEBUG_OUT("Context and argument can't be NULL");
        return;
    }

    DEBUG_OUT("set_user_id: %s", identifier);
    google::crashlytics::entry::jni::detail::managed_environment env(google::crashlytics::entry::jni::detail::jvm.load());
    google::crashlytics::detail::invokeN(env.get(), context->crashlytics_, context->set_user_id_, { identifier });
}

#if defined (CRASHLYTICS_DEBUG)

namespace {

//! Crashlytics.h is a stand-alone header. We need to ensure it compiles as part of the normal
//  build process.
void force_crashlytics_h_to_compile_as_cplusplus() __attribute__((unused));
void force_crashlytics_h_to_compile_as_cplusplus()
{
    using namespace firebase::crashlytics;

    Initialize();

    Log("message");
    SetCustomKey("key", "value");
    SetUserId("user");

    Terminate();

    //! Make sure everything is defined.
    external_api_initialize();
    external_api_dispose(nullptr);
    external_api_set(nullptr, "", "");
    external_api_log(nullptr, "");
    external_api_set_user_id(nullptr, "");
}

}

#endif
