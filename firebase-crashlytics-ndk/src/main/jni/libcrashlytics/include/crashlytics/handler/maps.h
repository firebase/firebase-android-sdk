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

#ifndef __CRASHLYTICS_HANDLER_MAPS_H__
#define __CRASHLYTICS_HANDLER_MAPS_H__

#include <array>
#include <sys/types.h>

#include "crashlytics/config.h"

namespace google { namespace crashlytics { namespace handler {
namespace detail {

constexpr std::size_t default_maps_entry_length() { return 128u; }
constexpr std::size_t default_maps_entry_count()  { return 2048u + 512u; }

enum Source { Self = 0, External };

template<std::size_t M> struct __attribute__ ((packed)) maps_entry {
public:
    uintptr_t       start;   // Start of the address range
    uintptr_t       end;     // End of the address range
    uintptr_t       offset;
    uintptr_t       base;
    int             flags;

    const char*     name;    // The name of the binary. This is a pointer into 'line'
    Source          source;
    char            line[M]; // The raw maps entry
};

template<typename>      struct maps_entry_length;
template<std::size_t M> struct maps_entry_length<maps_entry<M>> { static const std::size_t value = M; };

//! This is the raw storage for all maps entries.
typedef std::array<
    maps_entry<default_maps_entry_length()>, default_maps_entry_count()
> maps_entries_t;

//! Since the std::array is preallocated to a specific size, we need a way
//  to store the read count of entries.
template<typename Storage> struct maps {
public:
    typedef typename Storage::value_type entry_type;
    typedef typename Storage::size_type size_type;

    constexpr size_type upper_bound() const { return std::tuple_size<Storage>::value; }
    constexpr size_type entry_bound() const { return maps_entry_length<entry_type>::value; }

    maps() : count(0) {}
   ~maps() {}

    std::size_t     count;
    Storage         entries;
};

} // namespace detail

typedef detail::maps<detail::maps_entries_t> maps_t;

}}}

#endif // __CRASHLYTICS_HANDLER_MAPS_H__
