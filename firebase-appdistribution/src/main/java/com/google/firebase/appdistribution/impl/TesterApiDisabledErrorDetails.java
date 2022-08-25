// Copyright 2022 Google LLC
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

import androidx.annotation.Nullable;
import com.google.auto.value.AutoValue;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Details about a {@code SERVICE_DISABLED} error returned by the Tester API.
 *
 * <p>Error structure is described in the <a
 * href="https://cloud.google.com/apis/design/errors#error_details">Cloud APIs documentation</a>.
 */
@AutoValue
abstract class TesterApiDisabledErrorDetails {

  @AutoValue
  abstract static class HelpLink {
    abstract String description();

    abstract String url();

    static HelpLink create(String description, String url) {
      return new AutoValue_TesterApiDisabledErrorDetails_HelpLink(description, url);
    }
  }

  abstract List<HelpLink> helpLinks();

  String formatLinks() {
    StringBuilder stringBuilder = new StringBuilder();
    for (HelpLink link : helpLinks()) {
      stringBuilder.append(String.format("%s: %s\n", link.description(), link.url()));
    }
    return stringBuilder.toString();
  }

  /**
   * Try to parse API disabled error details from a response body.
   *
   * <p>If the response is an API disabled error but there is a failure parsing the help links, it
   * will still return the details with any links it could parse before the failure.
   *
   * @param responseBody
   * @return the details, or {@code null} if the response was not in the expected format
   */
  @Nullable
  static TesterApiDisabledErrorDetails tryParse(String responseBody) {
    try {
      // Get the error details object
      JSONArray details =
          new JSONObject(responseBody).getJSONObject("error").getJSONArray("details");
      JSONObject errorInfo = getDetailWithType(details, "type.googleapis.com/google.rpc.ErrorInfo");
      if (errorInfo.getString("reason").equals("SERVICE_DISABLED")) {
        return new AutoValue_TesterApiDisabledErrorDetails(parseHelpLinks(details));
      }
    } catch (JSONException e) {
      // Error was not in expected API disabled error format
    }
    return null;
  }

  private static JSONObject getDetailWithType(JSONArray details, String type) throws JSONException {
    for (int i = 0; i < details.length(); i++) {
      JSONObject detail = details.getJSONObject(i);
      if (detail.getString("@type").equals(type)) {
        return detail;
      }
    }
    throw new JSONException("No detail present with type: " + type);
  }

  static List<HelpLink> parseHelpLinks(JSONArray details) {
    List<HelpLink> helpLinks = new ArrayList<>();
    try {
      JSONObject help = getDetailWithType(details, "type.googleapis.com/google.rpc.Help");
      JSONArray linksJson = help.getJSONArray("links");
      for (int i = 0; i < linksJson.length(); i++) {
        helpLinks.add(parseHelpLink(linksJson.getJSONObject(i)));
      }
    } catch (JSONException e) {
      // If we have an issue parsing the links, we don't want to fail the entire error parsing, so
      // go ahead and return what we have
    }
    return helpLinks;
  }

  private static HelpLink parseHelpLink(JSONObject json) throws JSONException {
    String description = json.getString("description");
    String url = json.getString("url");
    return HelpLink.create(description, url);
  }
}
