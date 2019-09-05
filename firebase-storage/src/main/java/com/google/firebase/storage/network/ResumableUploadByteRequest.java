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

/** A request to upload a single chunk of a large blob. */
public class ResumableUploadByteRequest extends ResumableNetworkRequest {
  private final Uri uploadURL;
  private final byte[] chunk;
  private final long offset;
  private final boolean isFinal;
  private final int bytesToWrite;

  public ResumableUploadByteRequest(
      @NonNull Uri gsUri,
      @NonNull FirebaseApp app,
      @NonNull Uri uploadURL,
      @Nullable byte[] chunk,
      long offset,
      int bytesToWrite,
      boolean isFinal) {
    super(gsUri, app);
    if (chunk == null && bytesToWrite != -1) {
      super.mException = new IllegalArgumentException("contentType is null or empty");
    }
    if (offset < 0) {
      super.mException = new IllegalArgumentException("offset cannot be negative");
    }
    this.bytesToWrite = bytesToWrite;
    this.uploadURL = uploadURL;
    this.chunk = bytesToWrite <= 0 ? null : chunk;
    this.offset = offset;
    this.isFinal = isFinal;

    super.setCustomHeader(PROTOCOL, "resumable");
    if (this.isFinal && this.bytesToWrite > 0) {
      super.setCustomHeader(COMMAND, "upload, finalize");
    } else if (this.isFinal) {
      super.setCustomHeader(COMMAND, "finalize");
    } else {
      super.setCustomHeader(COMMAND, "upload");
    }
    super.setCustomHeader(OFFSET, Long.toString(this.offset));
  }

  @Override
  @NonNull
  protected String getAction() {
    return POST;
  }

  @Override
  @NonNull
  protected Uri getURL() {
    return uploadURL;
  }

  @Override
  @Nullable
  protected byte[] getOutputRaw() {
    return chunk;
  }

  @Override
  protected int getOutputRawSize() {
    return bytesToWrite > 0 ? bytesToWrite : 0;
  }
}
