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

package com.google.android.datatransport.cct;

import androidx.annotation.Keep;
import androidx.annotation.VisibleForTesting;
import com.google.android.datatransport.runtime.backends.BackendFactory;
import com.google.android.datatransport.runtime.backends.CreationContext;
import com.google.android.datatransport.runtime.backends.TransportBackend;

@Keep
public class CctBackendFactory implements BackendFactory {
  private static final String URL =
      mergeStrings("hts/frbslgiggolai.o/0clgbth", "tp:/ieaeogn.ogepscmvc/o/ac");

  @Override
  public TransportBackend create(CreationContext creationContext) {
    return new CctTransportBackend(
        creationContext.getApplicationContext(),
        URL,
        creationContext.getWallClock(),
        creationContext.getMonotonicClock());
  }

  @VisibleForTesting
  static String mergeStrings(String part1, String part2) {
    int sizeDiff = part1.length() - part2.length();
    if (sizeDiff < 0 || sizeDiff > 1) {
      throw new IllegalArgumentException("Invalid input received");
    }

    StringBuilder url = new StringBuilder(part1.length() + part2.length());

    for (int i = 0; i < part1.length(); i++) {
      url.append(part1.charAt(i));
      if (part2.length() > i) {
        url.append(part2.charAt(i));
      }
    }

    return url.toString();
  }
}
