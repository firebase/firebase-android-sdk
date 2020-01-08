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

#ifndef __CRASHLYTICS_DETAIL_SCOPED_WRITER_H__
#define __CRASHLYTICS_DETAIL_SCOPED_WRITER_H__

#include <cstdint>

namespace google { namespace crashlytics { namespace detail {
namespace impl {

void write(int fd, char value);
void write(int fd, uint64_t value);
void write(int fd, bool value);
void write(int fd, const char* value);
void write(int fd, const char* value, std::size_t length);
void write_sequence(int fd, const char* value);

} // namespace impl

struct scoped_writer {
    scoped_writer(int fd);
    scoped_writer(const scoped_writer &) = delete;
   ~scoped_writer();

    struct wrapped;

    void write(uint64_t value);
    void write(const char* value);
    void write(const char* value, std::size_t length);

    enum Delimiter {
        Comma,
        None,
        NewLine
    };

    template<typename T>
    void write(const char* key, T value, Delimiter delimiter = None);

    template<typename Iterator, typename Func>
    void write_array(const char* key, Iterator first, Iterator last, Func func, Delimiter delimiter = None);

private:
    int fd_;
};

int open(const char* filename);

}}}

//! implementation

struct google::crashlytics::detail::scoped_writer::wrapped {
    wrapped(const char* key, char open, char close, scoped_writer::Delimiter delimiter, scoped_writer& writer);
    wrapped(char open, char close, scoped_writer::Delimiter delimiter, scoped_writer& writer);
   ~wrapped();

private:
    const char*              key_;
    char                     close_;
    scoped_writer::Delimiter delimiter_;
    const scoped_writer&     writer_;
};

template<typename T>
inline void google::crashlytics::detail::scoped_writer::write(const char* key, T value, Delimiter delimiter)
{
    impl::write(fd_, key);
    impl::write(fd_, ':');
    impl::write(fd_, value);

    switch (delimiter) {
    case Comma:
        impl::write(fd_, ',');
        break;
    case NewLine:
        impl::write(fd_, '\n');
        break;
    case None:
        break;
    }
}


template<typename Iterator, typename Func>
inline void google::crashlytics::detail::scoped_writer::write_array(const char* key, Iterator first, Iterator last, Func func, Delimiter delimiter)
{
    wrapped outer(key, '[', ']', delimiter, *this);

    if (first == last) {
        return;
    }

    func(*first++, *this);

    while (first != last) {
        impl::write(fd_, ',');
        func(*first++, *this);
    }
}

#endif // __CRASHLYTICS_DETAIL_SCOPED_WRITER_H__