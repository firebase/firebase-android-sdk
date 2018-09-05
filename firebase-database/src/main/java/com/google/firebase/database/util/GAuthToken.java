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

package com.google.firebase.database.util;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents a "gauth" token used by the Server SDK, which can contain a token and optionally a
 * auth payload.
 *
 * <p>HACK: Rather than plumb GAuthToken through our internals we serialize it to/from a string
 * (using JSON) and pass it through our normal plumbing that expects token to be a String.
 */
public class GAuthToken {

  private final String token;
  private final Map<String, Object> auth;

  // Normal tokens will be JWTs or possibly Firebase Secrets, neither of which will contain "|"
  // so this should be a safe prefix.
  private static final String TOKEN_PREFIX = "gauth|";

  private static final String AUTH_KEY = "auth";
  private static final String TOKEN_KEY = "token";

  public GAuthToken(String token, Map<String, Object> auth) {
    this.token = token;
    this.auth = auth;
  }

  public static GAuthToken tryParseFromString(String rawToken) {
    if (!rawToken.startsWith(TOKEN_PREFIX)) {
      return null;
    }

    String gauthToken = rawToken.substring(TOKEN_PREFIX.length());
    try {
      Map<String, Object> tokenMap = JsonMapper.parseJson(gauthToken);
      String token = (String) tokenMap.get(TOKEN_KEY);
      @SuppressWarnings("unchecked")
      Map<String, Object> auth = (Map<String, Object>) tokenMap.get(AUTH_KEY);
      return new GAuthToken(token, auth);
    } catch (IOException e) {
      throw new RuntimeException("Failed to parse gauth token", e);
    }
  }

  public String serializeToString() {
    Map<String, Object> tokenMap = new HashMap<>();
    tokenMap.put(TOKEN_KEY, token);
    tokenMap.put(AUTH_KEY, auth);
    try {
      String json = JsonMapper.serializeJson(tokenMap);
      return TOKEN_PREFIX + json;
    } catch (IOException e) {
      throw new RuntimeException("Failed to serialize gauth token", e);
    }
  }

  public String getToken() {
    return this.token;
  }

  public Map<String, Object> getAuth() {
    return this.auth;
  }
}
