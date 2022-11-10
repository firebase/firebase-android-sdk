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

package com.google.firebase.annotations.concurrent;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;
import javax.inject.Qualifier;

/**
 * An executor/coroutine dispatcher for long running tasks including disk IO, heavy CPU
 * computations.
 *
 * <p>For operations that can block for long periods of time, like network requests, use the {@link
 * Blocking} executor.
 */
@Qualifier
@Target({ElementType.PARAMETER, ElementType.METHOD, ElementType.FIELD})
public @interface Background {}
