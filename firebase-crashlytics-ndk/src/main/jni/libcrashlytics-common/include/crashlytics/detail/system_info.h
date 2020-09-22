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

#ifndef __CRASHLYTICS_DETAIL_SYSTEM_INFO_H__
#define __CRASHLYTICS_DETAIL_SYSTEM_INFO_H__

#include <tuple>
#include <dirent.h>
#include <unistd.h>

#include <sys/statfs.h>

#include "crashlytics/config.h"
#include "crashlytics/detail/fgets_safe.h"
#include "crashlytics/detail/managed_node_open.h"
#include "crashlytics/detail/lexical_cast.h"

namespace google { namespace crashlytics { namespace detail {

template<typename T>
inline std::size_t extract(const char* entry, T& n)
{
    char* end;
    // The assumption here is that strtol is async safe. If, at some point,
    // we determine otherwise, it should be trivial to write this ourselves.
    return (n = std::strtol(entry, &end, 10)) != 0;
}

inline std::size_t read_battery_capacity(int fd)
{
    //! The capacity file shows values between 0 and 100.
    char capacity_string[4] = {};

    if (fgets_safe(fd, capacity_string, sizeof capacity_string, false) == nullptr) {
        DEBUG_OUT("Couldn't read the battery capacity");
        return 0u;
    }

    std::size_t capacity = 0;
    extract(capacity_string, capacity);

    return capacity;
}

inline std::pair<uint64_t, uint64_t> memory_statistics_from_sysconf()
{
    auto page_size = std::max(sysconf(_SC_PAGESIZE), 0L);
    return std::make_pair(
            std::max(sysconf(_SC_PHYS_PAGES),   0L) * page_size,    // Total physical memory, in bytes
            std::max(sysconf(_SC_AVPHYS_PAGES), 0L) * page_size     // Available physical memory, in bytes
    );
}

inline uint64_t parse_kb_value(const char* str, std::size_t length)
{
    while (*str == ' ') {
        ++str;
    }

    return crashlytics::detail::lexical_cast<uint64_t>(str, length);
}

template<std::size_t N>
inline uint64_t read_memory_statistics_from_proc_fragment(int fd, const char (&what)[N])
{
    char  buffer[256] = { 0 };
    char* p = nullptr;

    while ((p = fgets_safe(fd, buffer, sizeof buffer, false)) != nullptr &&
            strncmp(buffer, what, N - 1) != 0) {
    }

    lseek(fd, 0, SEEK_SET);

    return p == nullptr ? static_cast<uint64_t>(0) : parse_kb_value(
            buffer + N,
            sizeof buffer - 1 - N
    );
}

inline std::pair<uint64_t, uint64_t> memory_statistics_from_proc(int fd)
{
    uint64_t total = read_memory_statistics_from_proc_fragment(fd, "MemTotal:");
    uint64_t free  = read_memory_statistics_from_proc_fragment(fd, "MemFree:");

    //! Convert to bytes if present.
    return total == 0 || free == 0 ? memory_statistics_from_sysconf() : std::make_pair(
            total * 1024,
            free  * 1024
    );
}

//! Returns { total-physical-memory, available-physical-memory }
inline std::pair<uint64_t, uint64_t> memory_statistics()
{
    filesystem::managed_node_file managed("/proc/meminfo");
    return managed ? memory_statistics_from_proc(managed.fd()) : memory_statistics_from_sysconf();
}

//! Returns { total-internal-storage, available-internal-storage }
inline std::pair<uint64_t, uint64_t> internal_storage_statistics()
{
    struct statfs vfs;
    return RECOVER_FROM_INTERRUPT(statfs("/data", &vfs)) == 0 ? std::make_pair(
            static_cast<uint64_t>(vfs.f_blocks * vfs.f_bsize),   // Total internal storage, in bytes
            static_cast<uint64_t>(vfs.f_bfree  * vfs.f_bsize)    // Available internal storage, in bytes
    ) : std::make_pair(
            static_cast<uint64_t>(0),
            static_cast<uint64_t>(0)
    );
}

//! Return % battery remaining
inline std::size_t battery_capacity()
{
    //! Note, this path isn't the same for emulators.
    filesystem::managed_node_file managed("/sys/class/power_supply/battery/capacity");
    return managed ? read_battery_capacity(managed.fd()) : 0u;
}

}}} // namespace google::crashlytics::detail

#endif // __CRASHLYTICS_DETAIL_SYSTEM_INFO_H__
