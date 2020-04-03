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

package com.google.firebase;

import com.google.android.gms.common.annotation.KeepForSdk;

/**
 * Event sent when data collection default changes its value.
 *
 * @hide
 */
@KeepForSdk
public final class DataCollectionDefaultChange {
  /** The new value. */
  @KeepForSdk public final boolean enabled;

  @KeepForSdk
  public DataCollectionDefaultChange(boolean enabled) {
    this.enabled = enabled;
  }
}
