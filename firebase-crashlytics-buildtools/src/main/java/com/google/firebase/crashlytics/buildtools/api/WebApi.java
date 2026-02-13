/*
 * Copyright 2024 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.firebase.crashlytics.buildtools.api;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import org.apache.http.client.methods.HttpRequestBase;

public interface WebApi {

  String DEFAULT_CODEMAPPING_API_URL =
      mergeStrings("hts/frbscahyisybl.ogepscm", "tp:/ieaersltcsmosgolai.o");

  void applyCommonHeadersTo(HttpRequestBase request);

  /**
   * Generic interface for sending a multipart request w/file to a given URL.
   *
   * @param url The Crashlytics API url to which the file is to be sent.
   * @param file The file to be sent.
   * @throws IOException
   */
  void uploadFile(URL url, File file) throws IOException;

  String getCodeMappingApiUrl();

  void setUserAgent(String userAgent);

  void setClientType(String clientType);

  void setClientVersion(String clientVersion);

  static String mergeStrings(String part1, String part2) {
    int length = Math.max(part1.length(), part2.length());
    StringBuilder result = new StringBuilder(length);

    for (int i = 0; i < length; i++) {
      if (i < part1.length()) {
        result.append(part1.charAt(i));
      }
      if (i < part2.length()) {
        result.append(part2.charAt(i));
      }
    }

    return result.toString();
  }
}
