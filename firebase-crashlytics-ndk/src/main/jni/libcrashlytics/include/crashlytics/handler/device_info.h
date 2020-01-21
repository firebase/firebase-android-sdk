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

#ifndef __CRASHLYTICS_HANDLER_DEVICE_INFO_H__
#define __CRASHLYTICS_HANDLER_DEVICE_INFO_H__

#include "crashlytics/handler/detail/context.h"

namespace google { namespace crashlytics { namespace handler {

void write_device_info(const detail::context& handler_context, int fd);
void write_binary_libs(const detail::context& handler_context, int fd);

}}}

#endif // __CRASHLYTICS_HANDLER_DEVICE_INFO_H__