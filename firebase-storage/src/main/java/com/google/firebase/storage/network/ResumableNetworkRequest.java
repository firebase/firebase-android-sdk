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
import com.google.firebase.FirebaseApp;

/** Encapsulates a single resumable network request and response */
abstract class ResumableNetworkRequest extends NetworkRequest {

  @NonNull static final String PROTOCOL = "X-Goog-Upload-Protocol";
  @NonNull static final String COMMAND = "X-Goog-Upload-Command";
  @NonNull static final String CONTENT_TYPE = "X-Goog-Upload-Header-Content-Type";
  @NonNull static final String OFFSET = "X-Goog-Upload-Offset";

  ResumableNetworkRequest(@NonNull Uri gsUri, @NonNull FirebaseApp app) {
    super(gsUri, app);
  }
}
