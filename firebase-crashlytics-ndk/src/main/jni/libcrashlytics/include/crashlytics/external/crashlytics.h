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
#ifndef __CRASHLYTICS_H__
#define __CRASHLYTICS_H__

#if defined (__cplusplus)
        #include <cstddef>
#else
        #include <stdlib.h>
        #include <string.h>
#endif

#include <dlfcn.h>

// THIS IS THE LEGACY PUBLIC C++ API FOR THE FABRIC CRASHLYTICS NDK, INTENDED FOR BACKWARDS
// COMPATIBILITY ONLY. THIS API IS DEPRECATED AND SCHEDULED FOR REPLACEMENT IN 2020.

/*! Custom logs and keys ---------------------------------------------------------------------------------------*/
/*! Native API:

        crashlytics_context_t* crashlytics_init();
        void crashlytics_context_t::set(crashlytics_context_t* context, const char* key, const char* value);
        void crashlytics_context_t::log(crashlytics_context_t* context, const char* message);
        void crashlytics_context_t::set_user_id(crashlytics_context_t* context, const char* identifier);

        void crashlytics_free(crashlytics_context_t** context);

    Example:

        ...
        crashlytics_context_t* context = crashlytics_init();
        ...

        context->set(context, "key", "value");
        context->log(context, "this is a message");
        context->set_user_id(context, "identifier");

        ...
        crashlytics_free(&context);
        ...

 */

struct         __crashlytics_context;
struct         __crashlytics_unspecified;
typedef struct __crashlytics_context                    crashlytics_context_t;
typedef struct __crashlytics_unspecified                __crashlytics_unspecified_t;

typedef void   (*__crashlytics_set_t)                   (crashlytics_context_t *, const char *, const char *);
typedef void   (*__crashlytics_log_t)                   (crashlytics_context_t *, const char *);
typedef void   (*__crashlytics_set_user_id_t)           (crashlytics_context_t *, const char *);

typedef __crashlytics_unspecified_t*    (*__crashlytics_initialize_t)      ();
typedef void                            (*__crashlytics_set_internal_t)    (__crashlytics_unspecified_t *, const char *, const char *);
typedef void                            (*__crashlytics_log_internal_t)    (__crashlytics_unspecified_t *, const char *);
typedef void                            (*__crashlytics_dispose_t)         (__crashlytics_unspecified_t *);

typedef void                            (*__crashlytics_set_user_id_internal_t)(__crashlytics_unspecified_t *, const char *);

struct  __crashlytics_context {
/* API ---------------------------------------------------------------------------------------------------------*/
        __crashlytics_set_t                            set;
        __crashlytics_log_t                            log;
        __crashlytics_set_user_id_t                    set_user_id;
/*--------------------------------------------------------------------------------------------------------------*/

        __crashlytics_set_internal_t                   __set;
        __crashlytics_log_internal_t                   __log;
        __crashlytics_set_user_id_internal_t           __set_user_id;

        __crashlytics_unspecified_t*                   __ctx;
        __crashlytics_dispose_t                        __dispose;
};

#define __CRASHLYTICS_NULL_CONTEXT                             (struct __crashlytics_context *) 0
#define __CRASHLYTICS_INITIALIZE_FAILURE                       (struct __crashlytics_unspecified *) 0
#define __CRASHLYTICS_DECORATED                                __attribute__ ((always_inline))

/* API ---------------------------------------------------------------------------------------------------------*/

static inline crashlytics_context_t* crashlytics_init()                                   __CRASHLYTICS_DECORATED;
static inline void                   crashlytics_free(crashlytics_context_t** context)    __CRASHLYTICS_DECORATED;

/*! Implementation ---------------------------------------------------------------------------------------------*/

#define __CRASHLYTICS_NULL_ON_NULL(expression)                          \
    do {                                                                \
        if (((expression)) == NULL) {                                   \
            return NULL;                                                \
        }                                                               \
    } while (0)

static inline crashlytics_context_t* __crashlytics_allocate()                             __CRASHLYTICS_DECORATED;
static inline crashlytics_context_t* __crashlytics_allocate()
{
#if defined (__cplusplus)
    return new crashlytics_context_t;
#else
    crashlytics_context_t* context = (crashlytics_context_t *) malloc(sizeof (crashlytics_context_t));
    memset(context, 0, sizeof (crashlytics_context_t));
    return context;
#endif
}

static inline void __crashlytics_forward_context_to_set(crashlytics_context_t* context, const char* key, const char* value)                      __CRASHLYTICS_DECORATED;
static inline void __crashlytics_forward_context_to_set(crashlytics_context_t* context, const char* key, const char* value)
{
    context->__set(context->__ctx, key, value);
}

static inline void __crashlytics_forward_context_to_log(crashlytics_context_t* context, const char* message)                                     __CRASHLYTICS_DECORATED;
static inline void __crashlytics_forward_context_to_log(crashlytics_context_t* context, const char* message)
{
    context->__log(context->__ctx, message);
}

static inline void __crashlytics_forward_context_to_set_user_id(crashlytics_context_t* context, const char* identifier)                          __CRASHLYTICS_DECORATED;
static inline void __crashlytics_forward_context_to_set_user_id(crashlytics_context_t* context, const char* identifier)
{
    context->__set_user_id(context->__ctx, identifier);
}

static inline crashlytics_context_t* __crashlytics_construct(
        __crashlytics_unspecified_t* ctx, void* sym_set, void* sym_log, void* sym_dispose, void* sym_set_user_id
)  __CRASHLYTICS_DECORATED;
static inline crashlytics_context_t* __crashlytics_construct(
        __crashlytics_unspecified_t* ctx, void* sym_set, void* sym_log, void* sym_dispose, void* sym_set_user_id
)
{
    crashlytics_context_t* context;

    __CRASHLYTICS_NULL_ON_NULL(context = __crashlytics_allocate());

    context->set                   = (__crashlytics_set_t) __crashlytics_forward_context_to_set;
    context->log                   = (__crashlytics_log_t) __crashlytics_forward_context_to_log;
    context->set_user_id           = (__crashlytics_set_user_id_t) __crashlytics_forward_context_to_set_user_id;

    context->__set                 = (__crashlytics_set_internal_t) sym_set;
    context->__log                 = (__crashlytics_log_internal_t) sym_log;
    context->__set_user_id         = (__crashlytics_set_user_id_internal_t) sym_set_user_id;
    context->__ctx                 = ctx;
    context->__dispose             = (__crashlytics_dispose_t) sym_dispose;

    return context;
}

static inline crashlytics_context_t* crashlytics_init()
{
    void* lib;
    void* sym_ini;
    void* sym_log;
    void* sym_set;
    void* sym_dispose;
    void* sym_set_user_id;

    __CRASHLYTICS_NULL_ON_NULL(lib                     = dlopen("libcrashlytics.so", RTLD_LAZY | RTLD_LOCAL));
    __CRASHLYTICS_NULL_ON_NULL(sym_ini                 = dlsym(lib, "external_api_initialize"));
    __CRASHLYTICS_NULL_ON_NULL(sym_set                 = dlsym(lib, "external_api_set"));
    __CRASHLYTICS_NULL_ON_NULL(sym_log                 = dlsym(lib, "external_api_log"));
    __CRASHLYTICS_NULL_ON_NULL(sym_dispose             = dlsym(lib, "external_api_dispose"));
    __CRASHLYTICS_NULL_ON_NULL(sym_set_user_id         = dlsym(lib, "external_api_set_user_id"));

    __crashlytics_unspecified_t* ctx = ((__crashlytics_initialize_t) sym_ini)();

    return ctx == __CRASHLYTICS_INITIALIZE_FAILURE ? __CRASHLYTICS_NULL_CONTEXT : __crashlytics_construct(
            ctx,
            sym_set,
            sym_log,
            sym_dispose,
            sym_set_user_id
    );
}

static inline void __crashlytics_deallocate(crashlytics_context_t* context)  __CRASHLYTICS_DECORATED;
static inline void __crashlytics_deallocate(crashlytics_context_t* context)
{
#if defined (__cplusplus)
        delete context;
#else
        free(context);
#endif
}

static inline void crashlytics_free(crashlytics_context_t** context)
{
    if ((*context) != __CRASHLYTICS_NULL_CONTEXT) {
        (*context)->__dispose((*context)->__ctx);

        __crashlytics_deallocate(*context);

        (*context) = __CRASHLYTICS_NULL_CONTEXT;
    }
}

#endif /* __CRASHLYTICS_H__ */
