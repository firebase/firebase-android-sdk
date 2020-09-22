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

#ifndef __CRASHLYTICS_DETAIL_LEXICAL_CAST_H__
#define __CRASHLYTICS_DETAIL_LEXICAL_CAST_H__

#include <type_traits>
#include <algorithm>

namespace google { namespace crashlytics { namespace detail {

const char hex[] = "0123456789abcdef";

//! Converts the given integral into its textual representation.
/*! The buffer is assumed to have enough space. This function will only work for integral
    types
 */
template<typename T, typename Alphabet>
inline typename std::enable_if<std::is_integral<T>::value, std::size_t>::type lexical_cast(
        T            t,
        char*        buffer,
        unsigned int base,
        Alphabet     alphabet,
        const char*  default_value,
        std::size_t  default_value_length
)
{
    if (t == static_cast<T>(0)) {
        std::memcpy(buffer, default_value, default_value_length);
        return default_value_length;
    }

    std::size_t length = 0;

    while (t != 0) {
        buffer[length++] = alphabet(t % base);
        t /= base;
    }

    std::reverse(buffer, buffer + length);
    return length;
}

template<typename T>
inline typename std::enable_if<std::is_integral<T>::value, std::size_t>::type lexical_cast(T t, char* buffer)
{
    return lexical_cast(t, buffer, 10, [](T t) { return t + '0'; }, "0", 1);
}


template<typename T>
inline typename std::enable_if<std::is_integral<T>::value, std::size_t>::type lexical_cast_hex(T t, char* buffer)
{
    return lexical_cast(t, buffer, 16, [](T t) { return hex[t]; }, "00000000", 8);
}

template<std::size_t N> struct integral_converter;

template<> struct integral_converter<4> {
    static auto convert(const char* str) -> int { return atoi(str); }
};

template<> struct integral_converter<8> {
    static auto convert(const char* str) -> long long { return atoll(str); }
};

template<typename T>
inline typename std::enable_if<std::is_integral<T>::value, T>::type lexical_cast(const char* buffer, std::size_t length)
{
    const char* p = buffer;

    while ((*p < '0' || *p > '9') && static_cast<std::size_t>(std::distance(buffer, p)) < length) {
        ++p;
    }

    return integral_converter<sizeof (T)>::convert(p);
}

}}} // namespace google::crashlytics::detail

#endif // __CRASHLYTICS_DETAIL_LEXICAL_CAST_H__
