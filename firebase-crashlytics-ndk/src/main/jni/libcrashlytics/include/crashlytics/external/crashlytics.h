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
#ifndef __CRASHLYTICS_H__
#define __CRASHLYTICS_H__

#include <cstddef>
#include <string>
#include <memory>
#include <functional>
#include <dlfcn.h>

/// @brief Firebase Crashlytics NDK API, for Android apps which use native code.
///
/// This API is optional: It enables adding custom metadata to your native Crashlytics crash
/// reports. See <a href="https://firebase.google.com/docs/crashlytics">the developer guides</a>
/// for information on using Firebase Crashlytics in your NDK-enabled Android apps.
namespace firebase { namespace crashlytics {

/** PUBLIC API **/

/// @brief Initialize the Crashlytics NDK API, for Android apps using native code.
///
/// This allows finer grained control of when the native API is initialized. Calling this
/// function is not not strictly necessary as the API will be initialized on the first call
/// to any of the functions within the firebase::crashlytics namespace.
///
/// This call is only required for adding custom metadata to crash reports. Use of
/// this header file is NOT required for Android NDK crash reporting.
inline bool Initialize();

/// Deprecated; now a no-op and does not need to be called.
inline void Terminate();

/// @brief Logs a message to be included in the next fatal or non-fatal report.
inline void Log(const char* msg);

/// @brief Records a custom key and value to be associated with subsequent fatal and non-fatal
/// reports.
inline void SetCustomKey(const char* key, bool value);

/// @brief Records a custom key and value to be associated with subsequent fatal and non-fatal
/// reports.
inline void SetCustomKey(const char* key, const char *value);

/// @brief Records a custom key and value to be associated with subsequent fatal and non-fatal
/// reports.
inline void SetCustomKey(const char* key, double value);

/// @brief Records a custom key and value to be associated with subsequent fatal and non-fatal
/// reports.
inline void SetCustomKey(const char* key, float value);

/// @brief Records a custom key and value to be associated with subsequent fatal and non-fatal
/// reports.
inline void SetCustomKey(const char* key, int value);

/// @brief Records a custom key and value to be associated with subsequent fatal and non-fatal
/// reports.
inline void SetCustomKey(const char* key, long value);

/// @brief Records a user ID (identifier) that's associated with subsequent fatal and non-fatal
/// reports.
inline void SetUserId(const char* id);

/** END PUBLIC API **/

namespace detail {

struct         __crashlytics_context;
struct         __crashlytics_unspecified;
typedef struct __crashlytics_context                    __crashlytics_context_t;
typedef struct __crashlytics_unspecified                __crashlytics_unspecified_t;

typedef __crashlytics_unspecified_t*    (*__crashlytics_initialize_t)           ();
typedef void                            (*__crashlytics_set_internal_t)         (__crashlytics_unspecified_t *, const char *, const char *);
typedef void                            (*__crashlytics_log_internal_t)         (__crashlytics_unspecified_t *, const char *);
typedef void                            (*__crashlytics_set_user_id_internal_t) (__crashlytics_unspecified_t *, const char *);
typedef void                            (*__crashlytics_dispose_t)              (__crashlytics_unspecified_t *);

struct __crashlytics_context {
    __crashlytics_set_internal_t __set;
    __crashlytics_log_internal_t __log;
    __crashlytics_set_user_id_internal_t __set_user_id;

    __crashlytics_unspecified_t* __ctx;
    __crashlytics_dispose_t __dispose;
};

#define __CRASHLYTICS_NULL_CONTEXT                             (struct __crashlytics_context *) 0
#define __CRASHLYTICS_INITIALIZE_FAILURE                       (struct __crashlytics_unspecified *) 0
#define __CRASHLYTICS_DECORATED                                __attribute__ ((always_inline))

inline const __crashlytics_context_t* __crashlytics_context()                           __CRASHLYTICS_DECORATED;
inline __crashlytics_context_t*       __crashlytics_init()                              __CRASHLYTICS_DECORATED;

inline void invoke(const std::function<void (const __crashlytics_context_t *)>& func)   __CRASHLYTICS_DECORATED;

} // end namespace detail

inline bool Initialize() {
    return detail::__crashlytics_context() != nullptr;
}

inline void Terminate() {
    // no-op
}

inline void Log(const char* msg) {
    detail::invoke([&](const detail::__crashlytics_context_t* context) {
        context->__log(context->__ctx, msg);
    });
}

inline void SetCustomKey(const char* key, const char* value) {
    detail::invoke([&](const detail::__crashlytics_context_t* context) {
        context->__set(context->__ctx, key, value);
    });
}

inline void SetCustomKey(const char* key, bool value) {
    SetCustomKey(key, value ? "true" : "false");
}

inline void SetCustomKey(const char* key, double value) {
    SetCustomKey(key, std::to_string(value).c_str());
}

inline void SetCustomKey(const char* key, float value) {
    SetCustomKey(key, std::to_string(value).c_str());
}

inline void SetCustomKey(const char* key, int value) {
    SetCustomKey(key, std::to_string(value).c_str());
}

inline void SetCustomKey(const char* key, long value) {
    SetCustomKey(key, std::to_string(value).c_str());
}

inline void SetUserId(const char* id) {
    detail::invoke([&](const detail::__crashlytics_context_t* context) {
        context->__set_user_id(context->__ctx, id);
    });
}

namespace detail {

#define __CRASHLYTICS_NULL_ON_NULL(expression)                          \
    do {                                                                \
        if (((expression)) == NULL) {                                   \
            return NULL;                                                \
        }                                                               \
    } while (0)

inline __crashlytics_context_t* __crashlytics_construct(
        __crashlytics_unspecified_t* ctx, void* sym_set, void* sym_log, void* sym_dispose, void* sym_set_user_id)  __CRASHLYTICS_DECORATED;
inline __crashlytics_context_t* __crashlytics_construct(
        __crashlytics_unspecified_t* ctx, void* sym_set, void* sym_log, void* sym_dispose, void* sym_set_user_id) {
    __crashlytics_context_t* context;

    __CRASHLYTICS_NULL_ON_NULL(context = new __crashlytics_context_t);

    context->__set = (__crashlytics_set_internal_t) sym_set;
    context->__log = (__crashlytics_log_internal_t) sym_log;
    context->__set_user_id = (__crashlytics_set_user_id_internal_t) sym_set_user_id;
    context->__ctx = ctx;
    context->__dispose = (__crashlytics_dispose_t) sym_dispose;

    return context;
}

inline const __crashlytics_context_t* __crashlytics_context() {
    static std::unique_ptr<__crashlytics_context_t> context { __crashlytics_init() };
    return context.get();
}

inline __crashlytics_context_t* __crashlytics_init() {
    void* lib;
    void* sym_ini;
    void* sym_log;
    void* sym_set;
    void* sym_dispose;
    void* sym_set_user_id;

    __CRASHLYTICS_NULL_ON_NULL(lib = dlopen("libcrashlytics.so", RTLD_LAZY | RTLD_LOCAL));
    __CRASHLYTICS_NULL_ON_NULL(sym_ini = dlsym(lib, "external_api_initialize"));
    __CRASHLYTICS_NULL_ON_NULL(sym_set = dlsym(lib, "external_api_set"));
    __CRASHLYTICS_NULL_ON_NULL(sym_log = dlsym(lib, "external_api_log"));
    __CRASHLYTICS_NULL_ON_NULL(sym_dispose = dlsym(lib, "external_api_dispose"));
    __CRASHLYTICS_NULL_ON_NULL(sym_set_user_id = dlsym(lib, "external_api_set_user_id"));

    __crashlytics_unspecified_t* ctx = ((__crashlytics_initialize_t) sym_ini)();

    return ctx == __CRASHLYTICS_INITIALIZE_FAILURE
        ? __CRASHLYTICS_NULL_CONTEXT
        : __crashlytics_construct(
            ctx,
            sym_set,
            sym_log,
            sym_dispose,
            sym_set_user_id
        );
}

inline void invoke(const std::function<void (const __crashlytics_context_t *)>& func) {
    const __crashlytics_context_t* context = __crashlytics_context();
    if (context != nullptr) {
        func(context);
    }
}

} // end namespace detail

}}  // end namespace firebase::crashlytics

#endif /* __CRASHLYTICS_H__ */
