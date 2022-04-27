// Copyright 2020 Google LLC
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

package com.google.firebase.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Target;

/**
 * Indicates that the annotated symbol should be called from a {@code Deferred<T>} dependency.
 *
 * <p>This is particularly important for callback-style APIs in the context of <a
 * href="https://developer.android.com/guide/app-bundle/play-feature-delivery">dynamically loaded
 * modules</a>.
 *
 * @hide
 */
@Target({ElementType.METHOD, ElementType.CONSTRUCTOR})
@Inherited
public @interface DeferredApi {}
