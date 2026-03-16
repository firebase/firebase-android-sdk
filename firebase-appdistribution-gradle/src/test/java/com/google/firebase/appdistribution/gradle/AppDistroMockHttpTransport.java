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

package com.google.firebase.appdistribution.gradle;

import com.google.api.client.http.LowLevelHttpRequest;
import com.google.api.client.http.LowLevelHttpResponse;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpRequest;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;

public class AppDistroMockHttpTransport extends MockHttpTransport {
  private int code;
  private String content;

  private AppDistroMockHttpTransport() {}

  public static Builder newBuilder() {
    return new Builder();
  }

  @Override
  public LowLevelHttpRequest buildRequest(String method, String url) {
    return new MockLowLevelHttpRequest() {
      @Override
      public LowLevelHttpResponse execute() {
        MockLowLevelHttpResponse response = new MockLowLevelHttpResponse();
        response.setStatusCode(code);
        response.setContent(content);
        return response;
      }
    };
  }

  public static class Builder {
    private int code;
    private String content;

    public Builder setCode(int code) {
      this.code = code;
      return this;
    }

    public Builder setContent(String content) {
      this.content = content;
      return this;
    }

    public AppDistroMockHttpTransport build() {
      AppDistroMockHttpTransport transport = new AppDistroMockHttpTransport();
      transport.code = this.code;
      transport.content = this.content;
      return transport;
    }
  }
}
