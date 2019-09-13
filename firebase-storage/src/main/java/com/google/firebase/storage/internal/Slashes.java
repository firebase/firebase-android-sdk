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

package com.google.firebase.storage.internal;

import android.net.Uri;
import android.text.TextUtils;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.gms.common.internal.Preconditions;

/**
 * Utility methods for Firebase Storage.
 *
 * @hide
 */
public class Slashes {

  /**
   * URL encodes a string, but leaves slashes unmolested. This is used for encoding gs uri paths to
   * objects -- where the individual path segments need to be escaped, but the slashes do not.
   *
   * @param s The String to convert
   * @return A partially URL encoded string where slashes are preserved.
   */
  @NonNull
  public static String preserveSlashEncode(@Nullable String s) {
    if (TextUtils.isEmpty(s)) {
      return "";
    }
    return slashize(Uri.encode(s));
  }

  /**
   * Restores slashes within an encoded string.
   *
   * @param s The String to change
   * @return A modified string that replaces escaped slashes with unescaped slashes.
   */
  @NonNull
  public static String slashize(@NonNull String s) {
    Preconditions.checkNotNull(s);
    return s.replace("%2F", "/");
  }

  @NonNull
  @SuppressWarnings("StringSplitter")
  public static String normalizeSlashes(@NonNull String uriSegment) {
    if (TextUtils.isEmpty(uriSegment)) {
      return "";
    }
    if (uriSegment.startsWith("/") || uriSegment.endsWith("/") || uriSegment.contains("//")) {
      StringBuilder result = new StringBuilder();
      for (String stringSegment : uriSegment.split("/", -1)) {
        if (!TextUtils.isEmpty(stringSegment)) {
          if (result.length() > 0) {
            result.append("/").append(stringSegment);
          } else {
            result.append(stringSegment);
          }
        }
      }
      return result.toString();
    }
    return uriSegment;
  }
}
