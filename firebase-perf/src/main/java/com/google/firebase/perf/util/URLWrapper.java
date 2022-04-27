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

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;

/** Wrapper for URL class allowing unit testing */
public class URLWrapper {

  private final URL url;

  public URLWrapper(URL url) {
    this.url = url;
  }

  @SuppressWarnings("UrlConnectionChecker")
  public URLConnection openConnection() throws IOException {
    return url.openConnection();
  }

  public String toString() {
    return url.toString();
  }
}
