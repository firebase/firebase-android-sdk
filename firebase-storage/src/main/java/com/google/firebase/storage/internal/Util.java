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
import android.os.RemoteException;
import android.text.TextUtils;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.gms.common.internal.Objects;
import com.google.android.gms.common.internal.Preconditions;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.GetTokenResult;
import com.google.firebase.auth.internal.InternalAuthProvider;
import com.google.firebase.storage.network.NetworkRequest;
import java.io.UnsupportedEncodingException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Utility methods for Firebase Storage.
 *
 * @hide
 */
@SuppressWarnings("JavaDoc")
public class Util {
  public static final int NETWORK_UNAVAILABLE = -2;
  public static final String ISO_8601_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSSZ";
  private static final String TAG = "StorageUtil";
  private static final int MAXIMUM_TOKEN_WAIT_TIME_MS = 30000;

  public static long parseDateTime(@Nullable String dateString) {
    if (dateString == null) {
      return 0;
    }

    dateString = dateString.replaceAll("Z$", "-0000");

    SimpleDateFormat iso8601Format = new SimpleDateFormat(ISO_8601_FORMAT, Locale.getDefault());
    try {
      return iso8601Format.parse(dateString).getTime();
    } catch (ParseException e) {
      Log.w(TAG, "unable to parse datetime:" + dateString, e);
    }

    return 0;
  }

  /** Null-safe equivalent of {@code a.equals(b)}. */
  public static boolean equals(@Nullable Object a, @Nullable Object b) {
    return Objects.equal(a, b);
  }

  private static String getAuthority() throws RemoteException {
    return NetworkRequest.getAuthority();
  }

  /**
   * Normalizes a Firebase Storage uri into its "gs://" format and strips any trailing slash.
   *
   * @param s the url to normalize
   * @return a gs Uri parsed from the given string.
   */
  @Nullable
  public static Uri normalize(@NonNull FirebaseApp app, @Nullable String s)
      throws UnsupportedEncodingException {
    if (TextUtils.isEmpty(s)) {
      return null;
    }

    final String invalidUrlMessage =
        "Firebase Storage URLs must point to an object in your Storage Bucket. Please "
            + "obtain a URL using the Firebase Console or getDownloadUrl().";

    String trimmedInput = s.toLowerCase();
    String bucket;
    String encodedPath;
    if (trimmedInput.startsWith("gs://")) {
      String fullUri = Slashes.preserveSlashEncode(Slashes.normalizeSlashes(s.substring(5)));
      return Uri.parse("gs://" + fullUri);
    } else {
      Uri uri = Uri.parse(s);
      String scheme = uri.getScheme();

      if (scheme != null
          && (equals(scheme.toLowerCase(), "http") || equals(scheme.toLowerCase(), "https"))) {
        String lowerAuthority = uri.getAuthority().toLowerCase();
        int indexOfAuth;
        try {
          indexOfAuth = lowerAuthority.indexOf(getAuthority());
        } catch (RemoteException e) {
          throw new UnsupportedEncodingException(
              "Could not parse Url because the Storage network layer did not load");
        }
        encodedPath = Slashes.slashize(uri.getEncodedPath());
        if (indexOfAuth == 0 && encodedPath.startsWith("/")) {
          int firstBSlash = encodedPath.indexOf("/b/", 0); // /v0/b/bucket.storage
          // .firebase.com/o/child/image.png
          int endBSlash = encodedPath.indexOf("/", firstBSlash + 3);
          int firstOSlash = encodedPath.indexOf("/o/", 0);
          if (firstBSlash != -1 && endBSlash != -1) {
            bucket = encodedPath.substring(firstBSlash + 3, endBSlash);
            if (firstOSlash != -1) {
              encodedPath = encodedPath.substring(firstOSlash + 3);
            } else {
              encodedPath = "";
            }
          } else {
            Log.w(TAG, invalidUrlMessage);
            throw new IllegalArgumentException(invalidUrlMessage);
          }
        } else if (indexOfAuth > 1) {
          bucket = uri.getAuthority().substring(0, indexOfAuth - 1);
        } else {
          Log.w(TAG, invalidUrlMessage);
          throw new IllegalArgumentException(invalidUrlMessage);
        }
      } else {
        Log.w(TAG, "FirebaseStorage is unable to support the scheme:" + scheme);
        throw new IllegalArgumentException("Uri scheme");
      }
    }

    Preconditions.checkNotEmpty(bucket, "No bucket specified");
    return new Uri.Builder().scheme("gs").authority(bucket).encodedPath(encodedPath).build();
  }

  @Nullable
  public static String getCurrentAuthToken(@Nullable InternalAuthProvider authProvider) {
    try {
      String token = null;

      if (authProvider != null) {
        Task<GetTokenResult> pendingResult = authProvider.getAccessToken(false);
        GetTokenResult result =
            Tasks.await(pendingResult, MAXIMUM_TOKEN_WAIT_TIME_MS, TimeUnit.MILLISECONDS);
        token = result.getToken();
      }

      if (!TextUtils.isEmpty(token)) {
        return token;
      } else {
        Log.w(TAG, "no auth token for request");
      }
    } catch (ExecutionException | InterruptedException | TimeoutException e) {
      Log.e(TAG, "error getting token " + e);
    }
    return null;
  }
}
