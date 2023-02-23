// Copyright 2021 Google LLC
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

package com.google.firebase.appdistribution.impl;

import java.io.IOException;
import java.net.URL;
import javax.inject.Inject;
import javax.net.ssl.HttpsURLConnection;

class HttpsUrlConnectionFactory {

  @Inject
  HttpsUrlConnectionFactory() {}

  HttpsURLConnection openConnection(String url) throws IOException {
    return (HttpsURLConnection) new URL(url).openConnection();
  }
}
