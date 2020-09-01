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

#include <array>
#include <utility>
#include <atomic>
#include <cerrno>
#include <cstring>

#include "crashlytics/handler/device_info.h"
#include "crashlytics/handler/install.h"
#include "crashlytics/handler/detail/context.h"
#include "crashlytics/detail/memory/allocate.h"
#include "crashlytics/version.h"

#include "client/linux/handler/exception_handler.h"
#include "client/linux/handler/minidump_descriptor.h"

#include "system/log.h"

namespace google { namespace crashlytics { namespace detail {

extern int open(const char* filename);

}}}

namespace google { namespace crashlytics { namespace handler {
namespace detail { std::atomic<void *> instance(nullptr); }
namespace detail {

struct breakpad_context {
    static bool defer_to_next_handler;
    static bool callback(const google_breakpad::MinidumpDescriptor& descriptor, void* context, bool succeeded);

    explicit breakpad_context(const detail::context& handler_context);
   ~breakpad_context();

private:
    detail::context handler_context_;

    google_breakpad::MinidumpDescriptor descriptor_;
    google_breakpad::ExceptionHandler   handler_;
};

template<std::size_t N>
inline void make_suppliment_path_from(const char* path, const char* suffix, char (&buffer)[N])
{
    const char* extension            = std::strrchr(path, '.');
    std::size_t extensionless_length = std::distance(path, extension);
    std::size_t suffix_length        = std::strlen(suffix);

    std::memcpy(buffer, path, extensionless_length);
    std::memcpy(buffer + extensionless_length, suffix, suffix_length);
}

void finalize()
{
    DEBUG_OUT("Finalizing");
    breakpad_context* context = reinterpret_cast<breakpad_context *>(instance.load());
    crashlytics::detail::memory::release_storage<breakpad_context>(context);
}

} // namespace detail

bool detail::breakpad_context::defer_to_next_handler = false;

detail::breakpad_context::breakpad_context(const detail::context& handler_context)
    : handler_context_(handler_context),
      descriptor_(handler_context.filename),
      handler_(descriptor_, NULL, callback, reinterpret_cast<void *>(&handler_context_), true, -1)
{
}

detail::breakpad_context::~breakpad_context()
{
}

namespace detail {

template<typename WriteFunction, std::size_t BufferSize = 256u>
inline void write_supplimentary_file(const detail::context& handler_context, const char* minidump_path, const char* suffix, WriteFunction function)
{
    char supplimentary_path[BufferSize] = { 0 };
    make_suppliment_path_from(minidump_path, suffix, supplimentary_path);

    DEBUG_OUT("Supplementary file with suffix '%s' is at: %s", suffix, supplimentary_path);

    int fd;
    if ((fd = google::crashlytics::detail::open(supplimentary_path)) == -1) {
        DEBUG_OUT("Couldn't open supplementary file '%s'; %s", supplimentary_path, std::strerror(errno));
        return;
    }

    function(handler_context, fd);
}

} // namespace detail

bool detail::breakpad_context::callback(const google_breakpad::MinidumpDescriptor& descriptor, void* context, bool succeeded)
{
    const detail::context& handler_context = *reinterpret_cast<detail::context *>(context);

    DEBUG_OUT("Path is: %s; generating minidump %s", descriptor.path(), succeeded ? "succeeded" : "failed");

    detail::write_supplimentary_file(handler_context, descriptor.path(), ".device_info", [](const detail::context& handler_context, int fd) {
        google::crashlytics::handler::write_device_info(handler_context, fd);
    });

    detail::write_supplimentary_file(handler_context, descriptor.path(), ".maps", [](const detail::context& handler_context, int fd) {
        google::crashlytics::handler::write_binary_libs(handler_context, fd);
    });

    return defer_to_next_handler;
}

bool install_signal_handler(const detail::context& handler_context)
{
    if (detail::instance.load() == nullptr) {
        detail::instance.store(reinterpret_cast<void *>(
            crashlytics::detail::memory::allocate_storage<detail::breakpad_context>(handler_context)
        ));
    }

    return detail::instance.load() != nullptr;
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
extern "C" void debug_mode_is_enabled() {}
#endif
