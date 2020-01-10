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

import java.io.File;

final class SessionFiles {

  static final class Builder {
    private File minidump;
    private File binaryImages;
    private File metadata;
    private File session;
    private File app;
    private File device;
    private File os;

    Builder minidumpFile(File minidump) {
      this.minidump = minidump;
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

  public final File minidump;
  public final File binaryImages;
  public final File metadata;
  public final File session;
  public final File app;
  public final File device;
  public final File os;

  private SessionFiles(Builder builder) {
    minidump = builder.minidump;
    binaryImages = builder.binaryImages;
    metadata = builder.metadata;
    session = builder.session;
    app = builder.app;
    device = builder.device;
    os = builder.os;
  }
}
