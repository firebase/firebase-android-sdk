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

#ifndef __CRASHLYTICS_DETAIL_RECOVER_FROM_INTERRUPT_H__
#define __CRASHLYTICS_DETAIL_RECOVER_FROM_INTERRUPT_H__

#include <cerrno>

namespace google { namespace crashlytics { namespace detail {

//! Wrap system calls to ensure they are restarted if interrupted by a signal.
#define RECOVER_FROM_INTERRUPT(f)                                   \
    [&]() -> int {                                                  \
        int result;                                                 \
        while ((result = (f)) == -1 && errno == EINTR) {            \
        }                                                           \
        return result;                                              \
    }()

}}} // namespace google::crashlytics::detail

#endif // __CRASHLYTICS_DETAIL_RECOVER_FROM_INTERRUPT
