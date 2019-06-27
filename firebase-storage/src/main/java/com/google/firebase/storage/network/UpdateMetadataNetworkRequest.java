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

package com.google.firebase.storage.network;

import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.firebase.FirebaseApp;
import org.json.JSONObject;

/** Represents a request to update metadata on a GCS blob. */
public class UpdateMetadataNetworkRequest extends NetworkRequest {
  private final JSONObject metadata;

  public UpdateMetadataNetworkRequest(
      @NonNull Uri gsUri, @NonNull FirebaseApp app, @Nullable JSONObject metadata) {
    super(gsUri, app);
    this.metadata = metadata;
    // On kitkat and below, patch is not supported.
    this.setCustomHeader("X-HTTP-Method-Override", PATCH);
  }

  @NonNull
  @Override
  protected String getAction() {
    return PUT;
  }

  @Nullable
  @Override
  protected JSONObject getOutputJSON() {
    return metadata;
  }
}
