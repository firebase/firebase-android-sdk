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

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.firebase.crashlytics.internal.common.CommonUtils;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

public final class NativeFileUtils {
  private NativeFileUtils() {}

  @NonNull
  public static byte[] binaryImagesJsonFromMapsFile(@Nullable File file, @NonNull Context context)
      throws IOException {
    if (file == null || !file.exists()) {
      return new byte[0];
    }
    BufferedReader reader = null;
    try {
      reader = new BufferedReader(new FileReader(file));
      return new BinaryImagesConverter(context, new Sha1FileIdStrategy()).convert(reader);
    } finally {
      CommonUtils.closeQuietly(reader);
    }
  }
}
