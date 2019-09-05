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

package com.google.android.datatransport.cct;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.datatransport.runtime.Destination;

public final class CCTDestination implements Destination {
  static final String DESTINATION_NAME = "cct";
  public static final CCTDestination INSTANCE = new CCTDestination();

  private CCTDestination() {}

  @NonNull
  @Override
  public String getName() {
    return DESTINATION_NAME;
  }

  @Nullable
  @Override
  public byte[] getExtras() {
    return null;
  }
}
