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

#include "crashlytics/config.h"

#if defined (CRASHLYTICS_DEBUG)
#include "crashlytics/external/crashlytics.h"

static void force_crashlytics_h_to_compile_as_c() __attribute__((unused));
static void force_crashlytics_h_to_compile_as_c()
{
    crashlytics_context_t* context = crashlytics_init();

    context->set(context, "key", "value");
    context->log(context, "message");
    context->set_user_id(context, "identifier");
    context->set_user_name(context, "name");
    context->set_user_email(context, "email");

    crashlytics_free(&context);
}

#endif
