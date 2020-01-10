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

package com.google.firebase.crashlytics.internal.ndk;

import com.google.firebase.crashlytics.internal.common.CommonUtils;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/** Computes a SHA-1 hash of the file as its identifier. */
class Sha1FileIdStrategy implements BinaryImagesConverter.FileIdStrategy {
  @Override
  public String createId(File file) throws IOException {
    return getFileSHA(file.getPath());
  }

  /** Get the SHA-1 hash of a file. Assumes the file exists and is not empty. */
  private static String getFileSHA(String path) throws IOException {
    String sha = null;

    InputStream data = null;
    try {
      data = new BufferedInputStream(new FileInputStream(path));
      sha = CommonUtils.sha1(data);
    } finally {
      CommonUtils.closeQuietly(data);
    }
    return sha;
  }
}
