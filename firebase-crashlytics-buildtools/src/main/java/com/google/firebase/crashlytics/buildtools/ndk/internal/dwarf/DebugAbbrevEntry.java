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

package com.google.firebase.crashlytics.buildtools.ndk.internal.dwarf;

import java.util.List;

public class DebugAbbrevEntry {

  public final int number;
  public final DWTag tag;
  public final boolean hasChildren;
  public final List<Attribute> attributes;

  public DebugAbbrevEntry(int number, int tag, boolean hasChildren, List<Attribute> attributes) {
    this.number = number;
    this.tag = DWTag.fromValue(tag);
    this.hasChildren = hasChildren;
    this.attributes = attributes;
  }

  @Override
  public String toString() {
    final String hasChildrenString = hasChildren ? "[has children]" : "[no children]";
    final StringBuilder sb =
        new StringBuilder(number + "\t" + tag + "\t" + hasChildrenString + "\n");
    for (Attribute attr : attributes) {
      sb.append("  ").append(attr).append("\n");
    }
    return sb.toString();
  }

  public static class Attribute {
    public final DWAttribute name;
    public final DWForm form;

    public Attribute(int name, int type) {
      this.name = DWAttribute.fromValue(name);
      this.form = DWForm.fromValue(type);
    }

    @Override
    public String toString() {
      return name + "\t" + form;
    }
  }
}
