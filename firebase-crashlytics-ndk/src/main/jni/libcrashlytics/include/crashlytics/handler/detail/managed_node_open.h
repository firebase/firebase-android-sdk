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

#ifndef __CRASHLYTICS_HANDLER_DETAIL_MANAGED_NODE_OPEN_H__
#define __CRASHLYTICS_HANDLER_DETAIL_MANAGED_NODE_OPEN_H__

#include <algorithm>
#include <memory>
#include <cstring>
#include <dirent.h>

#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>

#include "crashlytics/config.h"
#include "crashlytics/handler/detail/recover_from_interrupt.h"
#include "crashlytics/detail/lexical_cast.h"

//! Provides a way to build buffers to the /proc pseudo-filesystem. Specifically,
//  we need to have an async-safe way of generating paths that will be passed in
//  to ::open() or ::opendir_r().

namespace google { namespace crashlytics { namespace handler { namespace detail {

//! max number of digits in PID_MAX (2^22)
constexpr std::size_t max_digits_in_pid_t() { return 7; }

namespace filesystem {

//! Given a buffer of appropriate size, this will add "<node><pid>" to it. The most used
//  case for this is to generate a buffer for "/proc/<pid>/<node>".
template<std::size_t N>
inline std::size_t concatenate(char* buffer, const char (&node)[N], pid_t pid)
{
    char pidstr[max_digits_in_pid_t() + 1] = { 0 };

    std::size_t pid_digit_length = google::crashlytics::detail::lexical_cast(pid, pidstr);

    std::memcpy(buffer, node, N - 1);
    std::memcpy(buffer + N - 1, pidstr, pid_digit_length);

    return N - 1 + pid_digit_length;
}

//! Applies func to the "/proc/<pid>/<node> family of nodes.
template<std::size_t R, std::size_t N, typename F, typename... Args>
inline auto apply_to(const char (&root)[R], pid_t pid, const char (&node)[N], F func, Args... args) -> decltype (func(node, args...))
{
    constexpr std::size_t length_fragment_root = R - 1;
    constexpr std::size_t length_fragment_node = N - 1;
    constexpr std::size_t length_fragment_pid = max_digits_in_pid_t();
    constexpr std::size_t length_buffer =
            length_fragment_root +
            length_fragment_node +
            length_fragment_pid;

    char buffer[length_buffer + 1] = { 0 };

    std::size_t offset = concatenate(buffer, root, pid);
    std::memcpy(buffer + offset, node, length_fragment_node);

    return func(buffer, args...);
}

//! Reads from "/proc/<pid>/<node>/<tid>/<sub-node> family of utilities.
template<std::size_t R, std::size_t M, std::size_t N, typename F, typename... Args>
inline auto apply_to(const char (&root)[R], pid_t pid, const char (&node)[M], pid_t tid, const char (&subnode)[N], F func, Args... args) -> decltype (func(node, args...))
{
    constexpr std::size_t length_fragment_root = R - 1;
    constexpr std::size_t length_fragment_node = M - 1;
    constexpr std::size_t length_fragment_pid = max_digits_in_pid_t();
    constexpr std::size_t length_fragment_tid = max_digits_in_pid_t();
    constexpr std::size_t length_fragment_sub = N - 1;
    constexpr std::size_t length_buffer =
            length_fragment_root +
            length_fragment_node +
            length_fragment_pid +
            length_fragment_tid +
            length_fragment_sub;

    char buffer[length_buffer + 1] = { 0 };

    std::size_t pid_offset = concatenate(buffer, root, pid);
    std::size_t tid_offset = concatenate(buffer + pid_offset, node, tid);
    std::memcpy(buffer + pid_offset + tid_offset, subnode, length_fragment_sub);

    return func(buffer, args...);
}

//! Opens and closes "/proc/<pid>/<node>" and "/proc/<pid>/<node>/<tid>/sub-node" files for read.
struct managed_node_file {
    template<std::size_t R, std::size_t N>
    managed_node_file(const char (&root)[R], pid_t pid, const char (&node)[N]);

    template<std::size_t R, std::size_t M, std::size_t N>
    managed_node_file(const char (&root)[R], pid_t pid, const char (&node)[M], pid_t tid, const char (&subnode)[N]);

    template<std::size_t M>
    managed_node_file(const char (&path)[M]);

   ~managed_node_file();

    int fd() const;

    operator bool() const;
private:
    int fd_;
};

//! Opens "/proc/<pid>/<node>" directories.
struct managed_node_dir {
    template<std::size_t R, std::size_t N>
    managed_node_dir(const char (&root)[R], pid_t pid, const char (&node)[N]);
   ~managed_node_dir();

    DIR* dir() const;

    operator bool() const;

private:
    std::unique_ptr<DIR, decltype (&::closedir)> dir_;
};

} // namespace filesystem

}}}}

//! implementation

template<std::size_t R, std::size_t N>
inline google::crashlytics::handler::detail::filesystem::managed_node_file::managed_node_file(const char (&root)[R], pid_t pid, const char (&node)[N])
    : fd_(-1)
{
    auto open_for_read = [](const char* filename) {
        return RECOVER_FROM_INTERRUPT(::open(filename, O_RDONLY));
    };

    if ((fd_ = apply_to(root, pid, node, open_for_read)) == -1) {
        DEBUG_OUT("apply returned -1 for node %s, (%d) %s", node, errno, strerror(errno));
    }
}

template<std::size_t R, std::size_t M, std::size_t N>
inline google::crashlytics::handler::detail::filesystem::managed_node_file::managed_node_file(const char (&root)[R], pid_t pid, const char (&node)[M], pid_t tid, const char (&subnode)[N])
    : fd_(-1)
{
    auto open_for_read = [](const char* filename) {
        return RECOVER_FROM_INTERRUPT(::open(filename, O_RDONLY));
    };

    if ((fd_ = apply_to(root, pid, node, tid, subnode, open_for_read)) == -1) {
        DEBUG_OUT("apply_to returned -1 for node %s, sub-node %s, (%d) %s", node, subnode, errno, strerror(errno));
    }
}

template<std::size_t M>
inline google::crashlytics::handler::detail::filesystem::managed_node_file::managed_node_file(const char (&path)[M])
    : fd_(-1)
{
    if ((fd_ = RECOVER_FROM_INTERRUPT(::open(path, O_RDONLY))) == -1) {
        DEBUG_OUT("::open returned -1 for %s", path);
    }
}

inline google::crashlytics::handler::detail::filesystem::managed_node_file::~managed_node_file()
{
    if (fd_ != -1 && ::close(fd_) == -1) {
        DEBUG_OUT("::close returned -1 for fd %d, (%d) %s", fd_, errno, strerror(errno));
    }
}

inline int google::crashlytics::handler::detail::filesystem::managed_node_file::fd() const
{
    return fd_;
}

inline google::crashlytics::handler::detail::filesystem::managed_node_file::operator bool() const
{
    return fd_ != -1;
}

template<std::size_t R, std::size_t N>
inline google::crashlytics::handler::detail::filesystem::managed_node_dir::managed_node_dir(const char (&root)[R], pid_t pid, const char (&node)[N])
    : dir_(apply_to(root, pid, node, ::opendir), ::closedir)
{
}

inline google::crashlytics::handler::detail::filesystem::managed_node_dir::~managed_node_dir()
{
}

inline DIR* google::crashlytics::handler::detail::filesystem::managed_node_dir::dir() const
{
    return dir_.get();
}

inline google::crashlytics::handler::detail::filesystem::managed_node_dir::operator bool() const
{
    return !!dir_;
}

#endif // __CRASHLYTICS_HANDLER_DETAIL_MANAGED_NODE_OPEN_H__
