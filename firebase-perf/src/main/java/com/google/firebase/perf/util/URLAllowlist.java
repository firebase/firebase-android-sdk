// Copyright 2020 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
//
// You may obtain a copy of the License at
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.firebase.perf.util;

import android.content.Context;
import android.content.res.Resources;
import androidx.annotation.NonNull;
import com.google.firebase.perf.logging.AndroidLogger;
import java.net.URI;

/** This class encapsulates the logic to determine whether a given URI is allowlisted. */
public class URLAllowlist {

  private static String[] allowlistedDomains;

  /** Returns true if the URL's domain covered by a allowlist rule. */
  public static boolean isURLAllowlisted(@NonNull URI uri, @NonNull Context appContext) {
    Resources resources = appContext.getResources();
    int resourceId =
        resources.getIdentifier(
            "firebase_performance_whitelisted_domains", "array", appContext.getPackageName());
    if (resourceId == 0) {
      return true;
    }

    AndroidLogger.getInstance()
        .debug("Detected domain allowlist, only allowlisted domains will be measured.");
    if (allowlistedDomains == null) {
      allowlistedDomains = resources.getStringArray(resourceId);
    }

    String host = uri.getHost();
    // There are certain valid URIs that don't contain a hostname, which we assume to be
    // allowlisted. Do note these will be filtered down the line, but for allowlisting purposes we
    // consider them to be valid.
    if (host == null) {
      return true;
    }

    for (String allowlistedDomain : allowlistedDomains) {
      if (host.contains(allowlistedDomain)) {
        return true;
      }
    }
    return false;
  }
}
