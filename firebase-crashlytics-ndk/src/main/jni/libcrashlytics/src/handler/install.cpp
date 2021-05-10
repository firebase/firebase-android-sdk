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

#include "crashlytics/config.h"
#include "crashlytics/handler/install.h"
#include "crashlytics/handler/detail/context.h"
#include "crashlytics/version.h"

namespace google { namespace crashlytics { namespace detail {

extern int open(const char* filename);

}}}

namespace google { namespace crashlytics { namespace handler {
namespace detail {

void* load_crashlytics_common()
{
    void* common = dlopen("libcrashlytics-common.so", RTLD_LAZY | RTLD_LOCAL);
    if (common == nullptr) {
        LOGE("Could not load libcrashlytics-common.so");
    }

    return common;
}

template<typename Func>
Func load_crashlytics_common_func(void* common, const std::string& func_name)
{
    if (common == nullptr) {
        return nullptr;
    }

    void* func_ptr = dlsym(common, func_name.c_str());
    if (func_ptr == nullptr) {
        LOGE("Could not find %s in libcrashlytics-common.so", func_name.c_str());
        return nullptr;
    }

    return reinterpret_cast<Func>(func_ptr);
}

} // namespace detail

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

bool install_signal_handler_java(
    const std::vector<std::string>* env,
    const detail::context& handler_context)
{
    using InstallSignalHandlerJava = bool (*)(
        const std::vector<std::string>* env,
        const detail::context& handler_context);

    InstallSignalHandlerJava install =
        detail::load_crashlytics_common_func<InstallSignalHandlerJava>(
            detail::load_crashlytics_common(),
            "install_signal_handler_java");
    
    return install && install(env, handler_context);
}

bool install_signal_handler_linker(
    const std::vector<std::string>* env,
    const detail::context& handler_context,
    const std::string& handler_trampoline,
    const std::string& handler_library)
{
    using InstallSignalHandlerLinker = bool (*)(
        const std::vector<std::string>* env,
        const detail::context& handler_context,
        const std::string& handler_trampoline,
        const std::string& handler_library);

    InstallSignalHandlerLinker install =
        detail::load_crashlytics_common_func<InstallSignalHandlerLinker>(
            detail::load_crashlytics_common(),
            "install_signal_handler_linker");
    
    return install &&
        install(env, handler_context, handler_trampoline, handler_library);
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

    LOGD("Initializing libcrashlytics version %s", VERSION);
    return install_signal_handler(handler_context);
}

}}}

extern "C" int CrashpadHandlerMain(int argc, char* argv[]) __attribute__((visibility ("default")));
extern "C" int CrashpadHandlerMain(int argc, char* argv[])
{
    using CrashpadHandlerMainFunc = int (*)(int, char **);
    using namespace google::crashlytics::handler;

    CrashpadHandlerMainFunc handler_main =
        detail::load_crashlytics_common_func<CrashpadHandlerMainFunc>(
            detail::load_crashlytics_common(), "CrashpadHandlerMain");
    
    return handler_main
        ? handler_main(argc, argv)
        : -1;
}

#if defined (CRASHLYTICS_DEBUG)
//! When building in debug, we can search for this symbol in the output of readelf or objdump, to verify
//  that a particular artifact has been compiled with this macro defined.
extern "C" void debug_mode_is_enabled() __attribute__((visibility ("default")));
void debug_mode_is_enabled() {}
#endif
