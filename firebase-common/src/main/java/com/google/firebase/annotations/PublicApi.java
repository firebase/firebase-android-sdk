// Copyright 2018 Google LLC
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

import com.google.android.gms.common.annotation.KeepForSdk;
import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

/**
 * Indicates that this object (class, method, etc) should be retained while preguarding an SDK, but
 * is safe to be proguarded away in the final app.
 *
 * <p>NOTE: For projects /not/ using proguard, the PublicApi annotation acts merely as a convention
 * and has no actual impact. These projects must abide by normal java visibility rules to govern the
 * visibility of methods in their public API.
 */
@KeepForSdk
@Target({ElementType.TYPE, ElementType.FIELD, ElementType.METHOD, ElementType.CONSTRUCTOR})
public @interface PublicApi {}
