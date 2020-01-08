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

#ifndef __CRASHLYTICS_HANDLER_DETAIL_FGETS_SAFE_H__
#define __CRASHLYTICS_HANDLER_DETAIL_FGETS_SAFE_H__

#include <algorithm>
#include <cstring>
#include <unistd.h>

#include <sys/types.h>

#include "crashlytics/config.h"
#include "crashlytics/handler/detail/recover_from_interrupt.h"

namespace google { namespace crashlytics { namespace handler { namespace detail {

inline std::size_t find_line_break(const char* line) __attribute__((always_inline));
inline std::size_t find_line_break(const char* line)
{
    if (const char* lb = std::strchr(line, '\n')) { return std::distance(line, lb) + 1; }
    if (const char* lb = std::strchr(line, '\0')) { return std::distance(line, lb); }

    return 0u;
}

inline char* fgets_safe(int fd, char* storage, std::size_t storage_size, bool debug = true)
{
    //! In the case that storage isn't large enough to hold a full line, we need to add
    //  a terminating character.
    std::memset(storage, 0, storage_size);
    storage_size -= 1;

    ssize_t bytes = RECOVER_FROM_INTERRUPT(
            ::read(fd, reinterpret_cast<void *>(storage), storage_size)
    );

    if (bytes == -1) {
        DEBUG_OUT("Failed to read from fd %d, (%d) %s", fd, errno, strerror(errno));
        return nullptr;
    }

    if (bytes == 0) {
        //! There is nothing more to read so bail.
        return nullptr;
    }

    std::size_t line_bound = std::min(storage_size, static_cast<std::size_t>(bytes));
    std::size_t line_break = find_line_break(storage);

    //! If we can't find a line break, storage isn't big enough to hold a full line.
    //  The offset will be the size of the line fragment.
    std::size_t offset = line_break != 0 ? line_break : storage_size;

    //! In the case that storage is too large, we might have multiple lines read.
    //  Zero out everything that isn't relevant to the current line.
    std::memset(storage + offset, 0, storage_size - offset);

    if (storage[offset - 1] == '\n') {
        storage[offset - 1] = '|';
    }

    DEBUG_OUT_IF(debug, "\t\t%s", storage);

    //! Adjust the file's file-offset to the size of the line.
    //  Lseek is uninterruptable.
    off_t current = ::lseek(fd, 0, SEEK_CUR);
    off_t adjusted __attribute__((unused)) = ::lseek(fd, current - line_bound + offset, SEEK_SET);

    return storage;
}

template<std::size_t N>
inline ssize_t read(int fd, char (&buffer)[N])
{
    return RECOVER_FROM_INTERRUPT(
            ::read(fd, reinterpret_cast<void *>(buffer), N)
    );
}

}}}}

#endif // __CRASHLYTICS_HANDLER_DETAIL_FGETS_SAGE_H__