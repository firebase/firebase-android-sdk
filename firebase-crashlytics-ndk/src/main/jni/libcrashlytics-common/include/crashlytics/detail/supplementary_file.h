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

#ifndef __CRASHLYTICS_DETAIL_SUPPLEMENTARY_FILE_H__
#define __CRASHLYTICS_DETAIL_SUPPLEMENTARY_FILE_H__

#include <cstring>
#include <cerrno>

#include "crashlytics/config.h"
#include "crashlytics/detail/scoped_writer.h"
#include "crashlytics/detail/device_info.h"

namespace google { namespace crashlytics { namespace detail {

template<std::size_t N>
inline void make_suppliment_path_from(const char* path, const char* suffix, char (&buffer)[N])
{
    const char* extension            = std::strrchr(path, '.');
    std::size_t extensionless_length = std::distance(path, extension);
    std::size_t suffix_length        = std::strlen(suffix);

    std::memcpy(buffer, path, extensionless_length);
    std::memcpy(buffer + extensionless_length, suffix, suffix_length);
}

template<typename WriteFunction, std::size_t BufferSize = 256u>
inline void write_supplimentary_file(const char* minidump_path, const char* suffix, WriteFunction function)
{
    char supplimentary_path[BufferSize] = { 0 };
    make_suppliment_path_from(minidump_path, suffix, supplimentary_path);

    DEBUG_OUT("Supplementary file with suffix '%s' is at: %s", suffix, supplimentary_path);

    int fd;
    if ((fd = google::crashlytics::detail::open(supplimentary_path)) == -1) {
        DEBUG_OUT("Couldn't open supplementary file '%s'; %s", supplimentary_path, std::strerror(errno));
        return;
    }

    function(fd);
}

}}} // namespace google::crashlytics::detail

#endif // __CRASHLYTICS_DETAIL_SUPPLEMENTARY_FILE_H__
