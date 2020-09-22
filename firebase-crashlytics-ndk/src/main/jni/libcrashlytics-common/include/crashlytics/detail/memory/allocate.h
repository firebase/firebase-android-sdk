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

#ifndef __CRASHLYTICS_DETAIL_MEMORY_ALLOCATE_H__
#define __CRASHLYTICS_DETAIL_MEMORY_ALLOCATE_H__

#include <memory>
#include "crashlytics/config.h"
#include "crashlytics/detail/memory/header.h"
#include "crashlytics/detail/memory/allocator.h"

//! Provides a wrapper around allocator.
/*! In the case where allocator is unable to mmap, we fall back to returning
    storage of the static duration as a last possible best effort.
*/

namespace google { namespace crashlytics { namespace detail { namespace memory {

//! Since the handler is serial, we can reuse already allocated static duration storage.
template<typename T>
inline void* make_function_scoped_static_byte_array()
{
    DEBUG_OUT("Couldn't use the page allocator, returning static storage of size %u", static_cast<unsigned int>(sizeof (T)));

    static std::size_t call_count = 0;
    static std::size_t size = sizeof (T) + sizeof (detail::header);

    static char storage[sizeof (T) + sizeof (detail::header)] = {};

    if (call_count++ > 0) {
        DEBUG_OUT("!!Static storage has already been allocated for this type");
        DEBUG_OUT("!!Program is ill-formed from this point");
    }

    std::memset(storage, 0, size);
    return detail::mark(&storage, detail::Static);
}

template<typename T>
inline T* get_static_storage()
{
    return new (make_function_scoped_static_byte_array<T>()) T();
}

template<typename T, typename U>
inline T* get_static_storage(U&& initial)
{
    return new (make_function_scoped_static_byte_array<T>()) T(std::move(initial));
}

//! Allocate storage from the kernel if possible, otherwise make a best effort attempt by
//  providing static storage. Keep in mind that this allocates new memory _once_ for every
//  new type. Calling this for the same type more than once will return the same storage.
template<typename T>
inline T* allocate_storage() noexcept
{
    page_allocator<T> allocator;

    T* storage = new (allocator.allocate(1)) T();
    T* value = storage != nullptr ? storage : get_static_storage<T>();
    return value;
}

template<typename T, typename U>
inline T* allocate_storage(U&& initial) noexcept
{
    page_allocator<T> allocator;

    T* storage = new (allocator.allocate(1)) T(std::move(initial));
    T* value = storage != nullptr ? storage : get_static_storage<T>(std::forward<U>(initial));
    return value;
}

template<typename T>
inline void release_storage(T* storage)
{
    if (storage != nullptr && detail::duration(storage) == detail::Mmap) {
        //! Deallocate only if there was no fallback to static storage.
        page_allocator<T>().deallocate(storage, sizeof (T));
    }
}

}}}} // namespace google::crashlytics::detail::memory

#endif // __CRASHLYTICS_DETAIL_MEMORY_ALLOCATE_H__
