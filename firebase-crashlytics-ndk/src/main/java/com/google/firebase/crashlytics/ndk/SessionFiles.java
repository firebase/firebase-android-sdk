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

package com.google.firebase.crashlytics.ndk;

import androidx.annotation.Nullable;
import com.google.firebase.crashlytics.internal.model.CrashlyticsReport;
import java.io.File;

final class SessionFiles {
  /**
   * Starting with Android S, it is possible to collect the tombstone upon an application restart.
   * In most cases, both, the tombstone and the minidump will be available.
   */
  static final class NativeCore {
    @Nullable public final File minidump;

    @Nullable public final CrashlyticsReport.ApplicationExitInfo applicationExitInfo;

    NativeCore(
        @Nullable File minidump,
        @Nullable CrashlyticsReport.ApplicationExitInfo applicationExitInfo) {
      this.minidump = minidump;
      this.applicationExitInfo = applicationExitInfo;
    }

    boolean hasCore() {
      // The classic case is that a crash occurred and a minidump was successfully captured. There
      // are two new cases to handle, however. First, a crash occurred and a minidump was not
      // captured - this is ok; check for a tombstone and use that instead. Second, a crash did not
      // occur because of a non-crashy GWP-ASan tombstone - this ok; capture just the tombstone.
      return (minidump != null && minidump.exists()) || applicationExitInfo != null;
    }
  }

  static final class Builder {
    private NativeCore nativeCore;
    private File binaryImages;
    private File metadata;
    private File session;
    private File app;
    private File device;
    private File os;

    Builder nativeCore(NativeCore nativeCore) {
      this.nativeCore = nativeCore;
      return this;
    }

    Builder binaryImagesFile(File binaryImages) {
      this.binaryImages = binaryImages;
      return this;
    }

    Builder metadataFile(File metadata) {
      this.metadata = metadata;
      return this;
    }

    Builder sessionFile(File session) {
      this.session = session;
      return this;
    }

    Builder appFile(File app) {
      this.app = app;
      return this;
    }

    Builder deviceFile(File device) {
      this.device = device;
      return this;
    }

    Builder osFile(File os) {
      this.os = os;
      return this;
    }

    SessionFiles build() {
      return new SessionFiles(this);
    }
  }

  public final NativeCore nativeCore;
  public final File binaryImages;
  public final File metadata;
  public final File session;
  public final File app;
  public final File device;
  public final File os;

  private SessionFiles(Builder builder) {
    nativeCore = builder.nativeCore;
    binaryImages = builder.binaryImages;
    metadata = builder.metadata;
    session = builder.session;
    app = builder.app;
    device = builder.device;
    os = builder.os;
  }
}
