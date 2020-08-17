// Copyright 2020 Google LLC
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

package com.google.firebase.firestore.core;

import static com.google.firebase.firestore.core.KeyFieldInFilter.extractDocumentKeysFromArrayValue;

import com.google.firebase.firestore.model.Document;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.FieldPath;
import com.google.firestore.v1.Value;
import java.util.ArrayList;
import java.util.List;

public class KeyFieldNotInFilter extends FieldFilter {
  private final List<DocumentKey> keys = new ArrayList<>();

  KeyFieldNotInFilter(FieldPath field, Value value) {
    super(field, Operator.NOT_IN, value);

    keys.addAll(extractDocumentKeysFromArrayValue(Operator.NOT_IN, value));
  }

  @Override
  public boolean matches(Document doc) {
    return !keys.contains(doc.getKey());
  }
}
