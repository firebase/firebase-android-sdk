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

package com.google.firebase.firestore.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A slash separated path for navigating resources (documents and collections) within Firestore. */
public final class ResourcePath extends BasePath<ResourcePath> {

  public static final ResourcePath EMPTY = new ResourcePath(Collections.emptyList());

  private ResourcePath(List<String> segments) {
    super(segments);
  }

  @Override
  ResourcePath createPathWithSegments(List<String> segments) {
    return new ResourcePath(segments);
  }

  public static ResourcePath fromSegments(List<String> segments) {
    return segments.isEmpty() ? ResourcePath.EMPTY : new ResourcePath(segments);
  }

  public static ResourcePath fromString(String path) {
    // NOTE: The client is ignorant of any path segments containing escape
    // sequences (e.g. __id123__) and just passes them through raw (they exist
    // for legacy reasons and should not be used frequently).

    if (path.contains("//")) {
      throw new IllegalArgumentException(
          "Invalid path (" + path + "). Paths must not contain // in them.");
    }

    // We may still have an empty segment at the beginning or end if they had a
    // leading or trailing slash (which we allow).
    @SuppressWarnings("StringSplitter")
    String[] rawSegments = path.split("/");
    ArrayList<String> segments = new ArrayList<>(rawSegments.length);
    for (String segment : rawSegments) {
      if (!segment.isEmpty()) {
        segments.add(segment);
      }
    }

    return new ResourcePath(segments);
  }

  @Override
  public String canonicalString() {
    // NOTE: The client is ignorant of any path segments containing escape
    // sequences (e.g. __id123__) and just passes them through raw (they exist
    // for legacy reasons and should not be used frequently).
    StringBuilder builder = new StringBuilder();
    for (int i = 0; i < segments.size(); i++) {
      if (i > 0) {
        builder.append("/");
      }
      builder.append(segments.get(i));
    }
    return builder.toString();
  }
}
