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

package com.google.firebase.installations.internal;

import androidx.annotation.NonNull;

/**
 * <aside class="warning"><strong>Provides an inter-operational interface only</strong>; instead,
 * use {@link FidListener} to call a listener when a Fid changes.</aside>
 *
 * @hide
 */
public interface FidListener {
  /**
   * <aside class="warning">Provides an inter-operational interface only; instead use {@link
   * FidListener}.</aside>
   *
   * <p>This method gets invoked when a Fid changes.
   *
   * @param fid represents the newly generated installation id.
   */
  void onFidChanged(@NonNull String fid);
}
