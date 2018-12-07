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

import com.google.firebase.components.Component;
import com.google.firebase.components.Dependency;
import java.util.Iterator;
import java.util.Set;

public class DefaultPlatformInfoSupplier implements PlatformInfoSupplier {
  private final String tokens;

  private DefaultPlatformInfoSupplier(Set<PlatformInfoToken> tokens) {
    this.tokens = toUserAgent(tokens);
  }

  @Override
  public String getUserAgent() {
    if (PlatformInfoCompat.getGlobalInfo().isEmpty()) {
      return tokens;
    }

    return tokens + ' ' + toUserAgent(PlatformInfoCompat.getGlobalInfo());
  }

  private static String toUserAgent(Set<PlatformInfoToken> tokens) {
    StringBuilder sb = new StringBuilder();
    Iterator<PlatformInfoToken> iterator = tokens.iterator();
    while (iterator.hasNext()) {
      PlatformInfoToken token = iterator.next();
      sb.append(token.key).append('/').append(token.value);
      if (iterator.hasNext()) {
        sb.append(' ');
      }
    }
    return sb.toString();
  }

  public static Component<PlatformInfoSupplier> component() {
    return Component.builder(PlatformInfoSupplier.class)
        .add(Dependency.setOf(PlatformInfoToken.class))
        .factory(c -> new DefaultPlatformInfoSupplier(c.setOf(PlatformInfoToken.class)))
        .build();
  }
}
