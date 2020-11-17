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

#ifndef __CRASHLYTICS_DETAIL_MEMORY_ALLOCATOR_H__
#define __CRASHLYTICS_DETAIL_MEMORY_ALLOCATOR_H__

#include <limits>
#include <cstdint>
#include <memory>
#include <cstring>

#include <unistd.h>
#include <sys/mman.h>

#include "crashlytics/config.h"
#include "crashlytics/detail/memory/header.h"

#if defined (CRASHLYTICS_DEBUG)
#    include <cerrno>
#endif

namespace google { namespace crashlytics { namespace detail { namespace memory {

//! Allocated memory via mmap. This allocator never unmaps because the process is crashing anyways, and
//  this allows us to declare the allocator as an automatic variable, instead of a static one. Accessing
//  variables of static scope duration in the context of the signal handler is discouraged.

template<typename T>
struct page_allocator {
public:
    typedef T              value_type;
    typedef T*             pointer;
    typedef const T*       const_pointer;
    typedef T&             reference;
    typedef const T&       const_reference;
    typedef std::size_t    size_type;
    typedef std::ptrdiff_t difference_type;

    template<typename U>
    struct rebind {
        typedef page_allocator<U> other;
    };

    pointer address(reference value);
    const_pointer address(const_reference value) const;

    page_allocator() noexcept;
    page_allocator(const page_allocator &) = delete;
   ~page_allocator();

    template<typename U>
    page_allocator(const page_allocator<U> &) = delete;

    size_type max_size() noexcept;
    pointer allocate(size_type size, const void* = 0);
    void deallocate(pointer p, size_type size);

    void construct(pointer p, const_reference value);
    void destroy(pointer p);

private:
    void*         allocate_pages_for_size(size_type size);
    std::uint8_t* allocate_pages(std::size_t page_count);

private:
    std::uint8_t* partial_page_;

    std::size_t page_size_;
    std::size_t page_offset_;
};

}}}} // namespace google::crashlytics::detail::memory

//! Implementation

template<typename T>
auto google::crashlytics::detail::memory::page_allocator<T>::address(reference value) -> pointer
{
    return &value;
}

template<typename T>
auto google::crashlytics::detail::memory::page_allocator<T>::address(const_reference value) const -> const_pointer
{
    return &value;
}

template<typename T>
google::crashlytics::detail::memory::page_allocator<T>::page_allocator() noexcept
    : partial_page_(nullptr), page_size_(std::max(sysconf(_SC_PAGESIZE), 0L)), page_offset_(0u)
{
}

template<typename T>
google::crashlytics::detail::memory::page_allocator<T>::~page_allocator()
{
}

template<typename T>
auto google::crashlytics::detail::memory::page_allocator<T>::max_size() noexcept -> size_type
{
    return 1024 * 1024 * 10;  //! Arbitrary limit
}

template<typename T>
auto google::crashlytics::detail::memory::page_allocator<T>::allocate(size_type size, const void *) -> pointer
{
    return size == 0u ? nullptr : static_cast<pointer>(allocate_pages_for_size(size * sizeof (T)));
}

namespace google { namespace crashlytics { namespace detail { namespace memory { namespace detail {

inline std::size_t page_count_for_size(std::size_t size, std::size_t page_size)
{
    return (size + sizeof (header) + page_size - 1) / page_size;
}

}}}}}

template<typename T>
void google::crashlytics::detail::memory::page_allocator<T>::deallocate(pointer p, size_type size)
{
    if (munmap(detail::unmarked(p), detail::page_count_for_size(size, page_size_)) == -1) {
        DEBUG_OUT("munmap() failed, errno = %d (%s)", errno, strerror(errno));
    }
}

template<typename T>
void google::crashlytics::detail::memory::page_allocator<T>::construct(pointer p, const_reference value)
{
    new (p) T(value);
}

template<typename T>
void google::crashlytics::detail::memory::page_allocator<T>::destroy(pointer p)
{
    p->~T();
}

namespace google { namespace crashlytics { namespace detail { namespace memory { namespace detail {

inline bool fits(std::uint8_t* current, std::size_t page_size, std::size_t page_offset, std::size_t size)
{
    return current != nullptr && (page_size - page_offset - sizeof (header) >= size);
}

inline bool full(std::size_t page_offset, std::size_t page_size)
{
    return page_offset == page_size;
}

inline void zero(std::size_t& page_offset, uint8_t*& page)
{
    page_offset = 0;
    page = nullptr;
}

inline std::uint8_t* pack(std::size_t page_size, std::size_t& page_offset, std::uint8_t*& page, std::size_t size)
{
    std::uint8_t* storage = page + page_offset;

    page_offset += size + sizeof (header);
    if (full(page_offset, page_size)) {
        zero(page_offset, page);
    }

    return reinterpret_cast<std::uint8_t *>(mark(storage, Ignorable));
}

}}}}}

template<typename T>
void* google::crashlytics::detail::memory::page_allocator<T>::allocate_pages_for_size(size_type size)
{
    if (detail::fits(partial_page_, page_size_, page_offset_, size)) {
        return detail::pack(page_size_, page_offset_, partial_page_, size);
    }

    std::size_t   page_count = detail::page_count_for_size(size, page_size_);
    std::uint8_t* page = allocate_pages(page_count);

    if (page == nullptr) {
        return nullptr;
    }

    page_offset_ = (page_size_ - (page_size_ * page_count - (size + sizeof (detail::header)))) % page_size_;
    partial_page_ = page_offset_ != 0 ? page + page_size_ * (page_count - 1) : nullptr;

    return detail::mark(page, detail::Mmap);
}

template<typename T>
std::uint8_t* google::crashlytics::detail::memory::page_allocator<T>::allocate_pages(std::size_t page_count)
{
    std::size_t size = page_size_ * page_count;
    void* raw = mmap(NULL, size, PROT_READ | PROT_WRITE, MAP_PRIVATE | MAP_ANON, -1, 0);

    if (raw == MAP_FAILED) {
        DEBUG_OUT("mmap() failed, errno = %d (%s)", errno, strerror(errno));
        return nullptr;
    }

    std::memset(raw, 0, size);
    return reinterpret_cast<std::uint8_t *>(raw);
}

#endif // __CRASHLYTICS_DETAIL_MEMORY_ALLOCATOR_H__
