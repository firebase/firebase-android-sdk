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

#include <string>
#include <vector>

#include <dlfcn.h>

#include <sys/system_properties.h>

#include "client/crashpad_client.h"

#include "crashlytics/config.h"
#include "crashlytics/handler/install.h"
#include "crashlytics/handler/detail/context.h"
#include "crashlytics/version.h"

namespace google { namespace crashlytics { namespace detail {

extern int open(const char* filename);

}}}

namespace google { namespace crashlytics { namespace handler {
namespace detail {

crashpad::CrashpadClient* GetCrashpadClient() {
    static crashpad::CrashpadClient* client = new crashpad::CrashpadClient();
    return client;
}

void finalize()
{
    DEBUG_OUT("Finalizing");
    delete GetCrashpadClient();
}

} // namespace detail

#if defined(__arm__) && defined(__ARM_ARCH_7A__)
#define CURRENT_ABI "armeabi-v7a"
#elif defined(__arm__)
#define CURRENT_ABI "armeabi"
#elif defined(__i386__)
#define CURRENT_ABI "x86"
#elif defined(__mips__)
#define CURRENT_ABI "mips"
#elif defined(__x86_64__)
#define CURRENT_ABI "x86_64"
#elif defined(__aarch64__)
#define CURRENT_ABI "arm64-v8a"
#else
#error "Unsupported target abi"
#endif

#if defined(ARCH_CPU_64_BITS)
  static constexpr bool kUse64Bit = true;
#else
  static constexpr bool kUse64Bit = false;
#endif

bool is_at_least_q()
{
    char api_level[PROP_VALUE_MAX] = {};
    if (__system_property_get("ro.build.version.sdk", api_level) && atoi(api_level) >= 29) {
        DEBUG_OUT("API level is Q+; %s", api_level);
        return true;
    }

    DEBUG_OUT("API level is pre-Q; %s", api_level);
    return false;
}

// Constructs paths to a handler trampoline executable and a library exporting
// the symbol `CrashpadHandlerMain()`. This requires this function to be built
// into the same object exporting this symbol and the handler trampoline is
// adjacent to it.
bool get_handler_trampoline(std::string& handler_trampoline, std::string& handler_library)
{
    // The linker doesn't support loading executables passed on its command
    // line until Q.
    if (!is_at_least_q()) {
        return false;
    }

    Dl_info info;
    if (dladdr(reinterpret_cast<void*>(&get_handler_trampoline), &info) == 0 ||
        dlsym(dlopen(info.dli_fname, RTLD_NOLOAD | RTLD_LAZY),
                "CrashpadHandlerMain") == nullptr) {

        DEBUG_OUT("Unable to find CrashpadHandlerMain; %s", info.dli_fname);
        return false;
    }

    DEBUG_OUT("Path for libcrashlytics.so is %s", info.dli_fname);

    std::string local_handler_library { info.dli_fname };

    size_t libdir_end = local_handler_library.rfind('/');
    if (libdir_end == std::string::npos) {
        DEBUG_OUT("Unable to find '/' in %s", local_handler_library.c_str());
        return false;
    }

    std::string local_handler_trampoline(local_handler_library, 0, libdir_end + 1);
    local_handler_trampoline += "libcrashlytics-trampoline.so";

    handler_trampoline.swap(local_handler_trampoline);
    handler_library.swap(local_handler_library);
    return true;
}

bool install_signal_handler_linker(
    const std::vector<std::string>* env,
    const detail::context& handler_context,
    const std::string& handler_trampoline,
    const std::string& handler_library)
{   
    base::FilePath database { handler_context.filename };
    base::FilePath metrics_dir;
    
    std::string url;
    std::map<std::string, std::string> annotations;
    std::vector<std::string> arguments;

    DEBUG_OUT("Installing Crashpad handler via trampoline");

    return detail::GetCrashpadClient()->StartHandlerWithLinkerAtCrash(
        handler_trampoline, handler_library, 
        kUse64Bit, env, database, metrics_dir, url, annotations, arguments
    );
}

bool install_signal_handler_java(
    const std::vector<std::string>* env,
    const detail::context& handler_context)
{
    std::string class_name = "com/google/firebase/crashlytics/ndk/CrashpadMain";
    
    base::FilePath database(handler_context.filename);
    base::FilePath metrics_dir;
    
    std::string url;
    std::map<std::string, std::string> annotations;
    std::vector<std::string> arguments;

    DEBUG_OUT("Installing Java Crashpad handler");

    return detail::GetCrashpadClient()->StartJavaHandlerAtCrash(
        class_name, env, database, metrics_dir, url, annotations, arguments);
}

bool install_signal_handler(const detail::context& handler_context)
{
    std::string handler_trampoline;
    std::string handler_library;
    std::string classpath = handler_context.classpath;
    std::string lib_path = handler_context.lib_path;

    std::vector<std::string> *env = new std::vector<std::string>;

    env->push_back("CLASSPATH=" + classpath);
    env->push_back("LD_LIBRARY_PATH=" + lib_path);
    env->push_back("ANDROID_DATA=/data");

    bool use_java_handler = !get_handler_trampoline(handler_trampoline, handler_library);

    return use_java_handler
        ? install_signal_handler_java(env, handler_context)
        : install_signal_handler_linker(env, handler_context, handler_trampoline, handler_library);
}

bool install_handlers(detail::context handler_context)
{
    DEBUG_OUT("!!Crashlytics is in debug mode!!");
    DEBUG_OUT("Path is %s", handler_context.filename);

    atexit(detail::finalize);

    LOGD("Initializing libcrashlytics version %s", VERSION);
    return install_signal_handler(handler_context);
}

}}}

#if defined (CRASHLYTICS_DEBUG)
//! When building in debug, we can search for this symbol in the output of readelf or objdump, to verify
//  that a particular artifact has been compiled with this macro defined.
extern "C" void debug_mode_is_enabled() __attribute__((visibility ("default")));
void debug_mode_is_enabled() {}
#endif
