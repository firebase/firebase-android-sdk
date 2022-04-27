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

package com.google.firebase.database.core.utilities;

import android.net.Uri;
import android.util.Base64;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.firebase.database.BuildConfig;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseException;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.core.Path;
import com.google.firebase.database.core.RepoInfo;
import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.Map;

public class Utilities {
  private static final char[] HEX_CHARACTERS = "0123456789abcdef".toCharArray();

  public static ParsedUrl parseUrl(@NonNull String url) throws DatabaseException {
    try {
      Uri uri = Uri.parse(url);

      String scheme = uri.getScheme();
      if (scheme == null) {
        throw new IllegalArgumentException("Database URL does not specify a URL scheme");
      }

      String host = uri.getHost();
      if (host == null) {
        throw new IllegalArgumentException("Database URL does not specify a valid host");
      }

      String namespace = uri.getQueryParameter("ns");
      if (namespace == null) {
        String[] parts = host.split("\\.", -1);
        namespace = parts[0].toLowerCase(Locale.US);
      }

      RepoInfo repoInfo = new RepoInfo();
      repoInfo.host = host.toLowerCase(Locale.US);
      int port = uri.getPort();
      if (port != -1) {
        repoInfo.secure = scheme.equals("https") || scheme.equals("wss");
        repoInfo.host += ":" + port;
      } else {
        repoInfo.secure = true;
      }

      repoInfo.internalHost = repoInfo.host;
      repoInfo.namespace = namespace;

      String originalPathString = extractPathString(url);
      // URLEncoding a space turns it into a '+', which is different
      // from our expected behavior. Do a manual replace to fix it.
      originalPathString = originalPathString.replace("+", " ");
      Validation.validateRootPathString(originalPathString);

      ParsedUrl parsedUrl = new ParsedUrl();
      parsedUrl.path = new Path(originalPathString);
      parsedUrl.repoInfo = repoInfo;

      return parsedUrl;
    } catch (Exception e) {
      throw new DatabaseException("Invalid Firebase Database url specified: " + url, e);
    }
  }

  /**
   * Extracts the path string from the original URL without changing the encoding (unlike
   * Uri.getPath()).
   */
  private static String extractPathString(String originalUrl) {
    int schemeOffset = originalUrl.indexOf("//");
    if (schemeOffset == -1) {
      throw new DatabaseException("Firebase Database URL is missing URL scheme");
    }

    String urlWithoutScheme = originalUrl.substring(schemeOffset + 2);
    int pathOffset = urlWithoutScheme.indexOf("/");
    if (pathOffset != -1) {
      int queryOffset = urlWithoutScheme.indexOf("?");
      if (queryOffset != -1) {
        return urlWithoutScheme.substring(pathOffset + 1, queryOffset);
      } else {
        return urlWithoutScheme.substring(pathOffset + 1);
      }
    } else {
      return "";
    }
  }

  public static String sha1HexDigest(String input) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-1");
      md.update(input.getBytes("UTF-8"));
      byte[] bytes = md.digest();
      return Base64.encodeToString(bytes, Base64.NO_WRAP);
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException("Missing SHA-1 MessageDigest provider.", e);
    } catch (UnsupportedEncodingException e) {
      throw new RuntimeException("UTF-8 encoding is required for Firebase Database to run!");
    }
  }

  public static String stringHashV2Representation(String value) {
    String escaped = value;
    if (value.indexOf('\\') != -1) {
      escaped = escaped.replace("\\", "\\\\");
    }
    if (value.indexOf('"') != -1) {
      escaped = escaped.replace("\"", "\\\"");
    }
    return '"' + escaped + '"';
  }

  public static String doubleToHashString(double value) {
    StringBuilder sb = new StringBuilder(16);
    long bits = Double.doubleToLongBits(value);
    // We use big-endian to encode the bytes
    for (int i = 7; i >= 0; i--) {
      int byteValue = (int) ((bits >>> (8 * i)) & 0xff);
      int high = ((byteValue >> 4) & 0xf);
      int low = (byteValue & 0xf);
      sb.append(HEX_CHARACTERS[high]);
      sb.append(HEX_CHARACTERS[low]);
    }
    return sb.toString();
  }

  // NOTE: We could use Ints.tryParse from guava, but I don't feel like pulling in guava (~2mb) for
  // that small purpose.
  public static Integer tryParseInt(String num) {
    if (num.length() > 11 || num.length() == 0) {
      return null;
    }
    int i = 0;
    boolean negative = false;
    if (num.charAt(0) == '-') {
      if (num.length() == 1) {
        return null;
      }
      negative = true;
      i = 1;
    }
    // long to prevent overflow
    long number = 0;
    while (i < num.length()) {
      char c = num.charAt(i);
      if (c < '0' || c > '9') {
        return null;
      }
      number = number * 10 + (c - '0');
      i++;
    }
    if (negative) {
      if (-number < Integer.MIN_VALUE) {
        return null;
      } else {
        return (int) -number;
      }
    } else {
      if (number > Integer.MAX_VALUE) {
        return null;
      }
      return (int) number;
    }
  }

  public static int compareInts(int i, int j) {
    if (i < j) {
      return -1;
    } else if (i == j) {
      return 0;
    } else {
      return 1;
    }
  }

  public static int compareLongs(long i, long j) {
    if (i < j) {
      return -1;
    } else if (i == j) {
      return 0;
    } else {
      return 1;
    }
  }

  public static <C> C castOrNull(Object o, Class<C> clazz) {
    if (clazz.isAssignableFrom(o.getClass())) {
      return (C) o;
    } else {
      return null;
    }
  }

  public static <C> C getOrNull(Object o, String key, Class<C> clazz) {
    if (o == null) {
      return null;
    }
    Map map = castOrNull(o, Map.class);
    Object result = map.get(key);
    if (result != null) {
      return castOrNull(result, clazz);
    } else {
      return null;
    }
  }

  public static void hardAssert(boolean condition) {
    hardAssert(condition, "");
  }

  public static void hardAssert(boolean condition, String message) {
    if (!condition) {
      if (BuildConfig.DEBUG) {
        throw new AssertionError("hardAssert failed: " + message);
      } else {
        Log.w("FirebaseDatabase", "Assertion failed: " + message);
      }
    }
  }

  public static Pair<Task<Void>, DatabaseReference.CompletionListener> wrapOnComplete(
      DatabaseReference.CompletionListener optListener) {
    if (optListener == null) {
      final TaskCompletionSource<Void> source = new TaskCompletionSource<>();
      DatabaseReference.CompletionListener listener =
          new DatabaseReference.CompletionListener() {
            @Override
            public void onComplete(DatabaseError error, DatabaseReference ref) {
              if (error != null) {
                source.setException(error.toException());
              } else {
                source.setResult(null);
              }
            }
          };
      return new Pair<>(source.getTask(), listener);
    } else {
      // If a listener is supplied we do not want to create a Task
      return new Pair<>(null, optListener);
    }
  }

  /** A nullable-aware equals method. */
  public static boolean equals(@Nullable Object left, @Nullable Object right) {
    if (left == right) {
      return true;
    }
    if (left == null || right == null) {
      return false;
    }
    return left.equals(right);
  }
}
