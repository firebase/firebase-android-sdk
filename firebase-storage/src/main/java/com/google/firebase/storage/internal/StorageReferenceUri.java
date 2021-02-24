// Copyright 2021 Google LLC
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

package com.google.firebase.storage.internal;

import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.firebase.emulators.EmulatedServiceSettings;
import com.google.firebase.storage.network.NetworkRequest;

/** Utility class to encapsulate the two "views" of a storage URI in a single object. */
public class StorageReferenceUri {

  private final Uri httpBaseUri;
  private final Uri httpUri;
  private final Uri gsUri;

  public StorageReferenceUri(@NonNull Uri gsUri) {
    this(gsUri, null);
  }

  public StorageReferenceUri(
      @NonNull Uri gsUri, @Nullable EmulatedServiceSettings emulatorSettings) {
    // We assume this has already come from Util.normalize()
    this.gsUri = gsUri;
    this.httpBaseUri =
        emulatorSettings == null
            ? NetworkRequest.PROD_BASE_URL
            : Uri.parse(
                "http://" + emulatorSettings.getHost() + ":" + emulatorSettings.getPort() + "/v0");

    // Add /b/bucket
    String bucket = gsUri.getAuthority();
    Uri.Builder httpBuilder = httpBaseUri.buildUpon().appendPath("b").appendEncodedPath(bucket);

    // Add /o/path (if there is a path)
    String path = Slashes.normalizeSlashes(gsUri.getPath());
    if (path.length() > 0 && !"/".equals(path)) {
      httpBuilder = httpBuilder.appendPath("o").appendPath(path);
    }

    this.httpUri = httpBuilder.build();
  }

  @NonNull
  public Uri getHttpBaseUri() {
    return httpBaseUri;
  }

  @NonNull
  public Uri getHttpUri() {
    return httpUri;
  }

  @NonNull
  public Uri getGsUri() {
    return gsUri;
  }
}
