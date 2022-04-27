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

package com.google.firebase.installations;

import androidx.annotation.NonNull;
import com.google.firebase.installations.internal.FidListener;

class FakeFidListener implements FidListener {
  private String currentFid;

  @Override
  public void onFidChanged(@NonNull String fid) {
    currentFid = fid;
  }

  public String getLatestFid() {
    return currentFid;
  }
}
