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

#ifndef __CRASHLYTICS_DETAIL_MEMORY_HEADER_H__
#define __CRASHLYTICS_DETAIL_MEMORY_HEADER_H__

namespace google { namespace crashlytics { namespace detail { namespace memory { namespace detail {

//! Defines the storage duration.
enum __attribute__((packed)) Duration {
        Static     = 0  // No need to deallocate.
      , Mmap       = 1  // Should be unmmaped.
      , Ignorable  = 2  // Ignore. This case happens when memory gets placed onto a partial page.
};

//! Using this header, we mark what the storage duration for a particular chunk of memory
//  is. This is necessary in order to prevent unmmap-ing memory that wasn't mmaped.
struct __attribute__((packed)) header {
    Duration duration;

    // This padding is necessary to ensure correct alignment on certain
    // architectures; namely, arm.
    char padding[7];
};

//! Be careful here; p _must_ be a marked pointer, otherwise the behavior is undefined.
inline Duration duration(const void* marked) __attribute__((always_inline));
inline Duration duration(const void* marked)
{
    static_assert(sizeof (header) == 8, "This architecture yields an incorrect header packing");

    const char*   q = reinterpret_cast<const char *>(marked);
    const header* h = reinterpret_cast<const header *>(q - sizeof (header));

    return h->duration;
}

inline void* mark(void* unmarked, Duration duration) __attribute__((always_inline));
inline void* mark(void* unmarked, Duration duration)
{
    char*   q = reinterpret_cast<char *>(unmarked);
    header* h = reinterpret_cast<header *>(q);

    h->duration = duration;
    return q + sizeof (header);
}

inline void* unmarked(void* marked) __attribute__((always_inline));
inline void* unmarked(void* marked)
{
    return reinterpret_cast<char *>(marked) - sizeof (header);
}

}}}}} // namespace google::crashlytics::detail::memory::detail

#endif // __CRASHLYTICS_DETAIL_MEMORY_HEADER_H__
