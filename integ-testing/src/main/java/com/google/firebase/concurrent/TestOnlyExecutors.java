// Copyright 2022 Google LLC
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

package com.google.firebase.concurrent;

import androidx.annotation.RestrictTo;

import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;

@RestrictTo(RestrictTo.Scope.TESTS)
public class TestOnlyExecutors {
    public static Executor ui() {
        return UiExecutor.INSTANCE;
    }

    public static ScheduledExecutorService background() {
        return ExecutorsRegistrar.BG_EXECUTOR.get();
    }

    public static ScheduledExecutorService blocking() {
        return ExecutorsRegistrar.BLOCKING_EXECUTOR.get();
    }

    public static ScheduledExecutorService lite() {
        return ExecutorsRegistrar.LITE_EXECUTOR.get();
    }
}
