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

package com.google.firebase.platforminfo;

import java.util.Iterator;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Provides a user agent string that captures the SDKs and their corresponding versions.
 *
 * <p>Example user agent string: "firebase-common/16.1.1 firebase-firestore/16.1.2
 * firebase-database/16.1.2"
 */
@Singleton
public class DefaultUserAgentPublisher implements UserAgentPublisher {
  private final String javaSDKVersionUserAgent;
  private final GlobalLibraryVersionRegistrar gamesSDKRegistrar;

  @Inject
  DefaultUserAgentPublisher(
      Set<LibraryVersion> libraryVersions, GlobalLibraryVersionRegistrar gamesSDKRegistrar) {
    this.javaSDKVersionUserAgent = toUserAgent(libraryVersions);
    this.gamesSDKRegistrar = gamesSDKRegistrar;
  }

  /**
   * Returns the user agent string that is computed as follows 1. For our JavaSDKs, the string is
   * computed in advance since the components framework guarantees that we receive all published
   * versions. 2. For our GamesSDKs, the strings are recomputed each time since the registration of
   * the versions happens out of band and we take the optimistic approach of recomputing each time.
   */
  @Override
  public String getUserAgent() {
    if (gamesSDKRegistrar.getRegisteredVersions().isEmpty()) {
      return javaSDKVersionUserAgent;
    }

    return javaSDKVersionUserAgent + ' ' + toUserAgent(gamesSDKRegistrar.getRegisteredVersions());
  }

  private static String toUserAgent(Set<LibraryVersion> tokens) {
    StringBuilder sb = new StringBuilder();
    Iterator<LibraryVersion> iterator = tokens.iterator();
    while (iterator.hasNext()) {
      LibraryVersion token = iterator.next();
      sb.append(token.getLibraryName()).append('/').append(token.getVersion());
      if (iterator.hasNext()) {
        sb.append(' ');
      }
    }
    return sb.toString();
  }
}
