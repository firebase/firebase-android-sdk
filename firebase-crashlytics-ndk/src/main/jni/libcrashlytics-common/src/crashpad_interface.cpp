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

#include <vector>
#include <string>

#include "client/crashpad_client.h"
#include "crashlytics/config.h"
#include "crashlytics/detail/abi.h"
#include "crashlytics/handler/detail/context.h"

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

extern "C" {

bool install_signal_handler_linker(
    const std::vector<std::string>* env,
    const detail::context& handler_context,
    const std::string& handler_trampoline,
    const std::string& handler_library
) __attribute__((visibility ("default")));

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
    atexit(detail::finalize);

    return detail::GetCrashpadClient()->StartHandlerWithLinkerAtCrash(
        handler_trampoline, handler_library, 
        kUse64Bit, env, database, metrics_dir, url, annotations, arguments
    );
}

bool install_signal_handler_java(
    const std::vector<std::string>* env,
    const detail::context& handler_context) __attribute__((visibility ("default")));

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

    arguments.push_back(env->back());

    DEBUG_OUT("Installing Java Crashpad handler");
    atexit(detail::finalize);

    return detail::GetCrashpadClient()->StartJavaHandlerAtCrash(
        class_name, env, database, metrics_dir, url, annotations, arguments);
}

} // extern "C"

} // namespace detail
}}} // namespace google::crashlytics::handler