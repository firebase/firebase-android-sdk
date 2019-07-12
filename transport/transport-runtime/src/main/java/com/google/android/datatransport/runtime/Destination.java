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

package com.google.android.datatransport.runtime;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public interface Destination {
  /** Name that can be used to discover the backend */
  @NonNull
  String getName();

  /**
   * Any extras that must be passed to the backend while uploading. Uploads to the backend are
   * grouped by (backend_name, priority, extras)
   *
   * <p>Note that backends that change the implementations of this method must prepare to
   * deserialize older implementations as well. For events that have already been written to disk,
   * extras may be at older versions.
   */
  @Nullable
  byte[] getExtras();
}
