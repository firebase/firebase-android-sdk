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

#include <cstring>
#include <algorithm>

#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <unistd.h>

#include "crashlytics/config.h"
#include "crashlytics/detail/scoped_writer.h"
#include "crashlytics/detail/lexical_cast.h"

void google::crashlytics::detail::impl::write(int fd, char value)
{
    ::write(fd, &value, sizeof (char));
}

void google::crashlytics::detail::impl::write(int fd, uint64_t value)
{
    char buffer[20] = { 0 };
    std::size_t length = crashlytics::detail::lexical_cast(value, buffer);

    ::write(fd, buffer, length);
}

void google::crashlytics::detail::impl::write(int fd, bool value)
{
    impl::write_sequence(fd, value ? "true" : "false");
}

void google::crashlytics::detail::impl::write(int fd, const char* value)
{
    std::size_t length = std::strlen(value);

    ::write(fd, "\"", sizeof (char));
    ::write(fd, value, std::max(value[length - 1] == '\n' ? length - 1 : length, static_cast<std::size_t>(0)));
    ::write(fd, "\"", sizeof (char));
}

void google::crashlytics::detail::impl::write(int fd, const char* value, std::size_t length)
{
    ::write(fd, value, length);
}

void google::crashlytics::detail::impl::write_sequence(int fd, const char* value)
{
    ::write(fd, value, std::strlen(value));
}

int google::crashlytics::detail::open(const char* filename)
{
    return ::open(filename, O_WRONLY | O_CREAT | O_TRUNC, 0644);
}

google::crashlytics::detail::scoped_writer::wrapped::wrapped(const char* key, char open_char, char close_char, scoped_writer::Delimiter delimiter, scoped_writer& writer)
    : key_(key), close_(close_char), delimiter_(delimiter), writer_(writer)
{
    if (key != nullptr) {
        impl::write(writer.fd_, key_);
        impl::write(writer.fd_, ':');
    }

    impl::write(writer.fd_, open_char);
}

google::crashlytics::detail::scoped_writer::wrapped::wrapped(char open_char, char close_char, scoped_writer::Delimiter delimiter, scoped_writer& writer)
    : wrapped(nullptr, open_char, close_char, delimiter, writer)
{
}

google::crashlytics::detail::scoped_writer::wrapped::~wrapped()
{
    impl::write(writer_.fd_, close_);

    switch (delimiter_) {
    case scoped_writer::Comma:
        impl::write(writer_.fd_, ',');
        break;
    case scoped_writer::NewLine:
        impl::write(writer_.fd_, '\n');
        break;
    case scoped_writer::None:
        break;
    }
}

google::crashlytics::detail::scoped_writer::scoped_writer(int fd) : fd_(fd)
{
}

google::crashlytics::detail::scoped_writer::~scoped_writer()
{
    if (::fsync(fd_) == -1) {
        //! no-op at the moment
    }
    if (::close(fd_) == -1) {
        //! no-op at the moment
    }
}

void google::crashlytics::detail::scoped_writer::write(uint64_t value)
{
    impl::write(fd_, value);
}

void google::crashlytics::detail::scoped_writer::write(const char* value)
{
    impl::write(fd_, value);
}

void google::crashlytics::detail::scoped_writer::write(const char* value, std::size_t length)
{
    impl::write(fd_, value, length);
}
