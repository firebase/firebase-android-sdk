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

package com.google.firebase.firestore.bundle;

import org.json.JSONException;
import org.json.JSONObject;

public abstract class BundleElement {

  public static BundleElement fromJson(BundleSerializer serializer, String json)
      throws JSONException {
    JSONObject object = new JSONObject(json);

    if (object.has("metadata")) {
      return serializer.decodeBundleMetadata(object.getJSONObject("metadata"));
    } else if (object.has("namedQuery")) {
      return serializer.decodeNamedQuery(object.getJSONObject("namedQuery"));
    } else if (object.has("documentMetadata")) {
      return serializer.decodeBundledDocumentMetadata(object.getJSONObject("documentMetadata"));
    } else if (object.has("document")) {
      return serializer.decodeDocument(object.getJSONObject("document"));
    } else {
      throw new IllegalArgumentException("Cannot decode unknown Bundle element: " + json);
    }
  }

  protected BundleElement() {}
}
