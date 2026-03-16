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

import static org.junit.Assert.assertEquals;

import com.google.api.client.googleapis.auth.oauth2.GoogleRefreshTokenRequest;
import com.google.api.client.http.LowLevelHttpRequest;
import com.google.api.client.http.LowLevelHttpResponse;
import com.google.api.client.http.UrlEncodedContent;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpRequest;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;

class MockVerifyRefreshToken extends MockHttpTransport {
  private final String refreshToken;
  private final String successResponse;

  MockVerifyRefreshToken(String refreshToken, String successResponse) {
    this.refreshToken = refreshToken;
    this.successResponse = successResponse;
  }

  @Override
  public LowLevelHttpRequest buildRequest(String method, String url) {
    return new MockLowLevelHttpRequest() {
      @Override
      public LowLevelHttpResponse execute() {

        UrlEncodedContent content = (UrlEncodedContent) this.getStreamingContent();
        GoogleRefreshTokenRequest request = (GoogleRefreshTokenRequest) content.getData();

        assertEquals(refreshToken, request.getRefreshToken());

        MockLowLevelHttpResponse response = new MockLowLevelHttpResponse();
        response.setStatusCode(200);
        response.setContent(successResponse);
        return response;
      }
    };
  }
}
