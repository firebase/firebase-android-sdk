/*
 * Copyright 2024 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.firebase.dataconnect.testutil

import com.google.firebase.dataconnect.DataConnectPathSegment
import com.google.protobuf.Value

typealias ProtoValuePathComponent = DataConnectPathSegment

typealias StructKeyProtoValuePathComponent = DataConnectPathSegment.Field

typealias ListElementProtoValuePathComponent = DataConnectPathSegment.ListIndex

typealias MutableProtoValuePath = MutableList<ProtoValuePathComponent>

typealias ProtoValuePath = List<ProtoValuePathComponent>

data class ProtoValuePathPair(val path: ProtoValuePath, val value: Value)

object ProtoValuePathComparator : Comparator<ProtoValuePath> {
  override fun compare(o1: ProtoValuePath, o2: ProtoValuePath): Int {
    val size = o1.size.coerceAtMost(o2.size)
    repeat(size) {
      val componentComparisonResult = ProtoValuePathComponentComparator.compare(o1[it], o2[it])
      if (componentComparisonResult != 0) {
        return componentComparisonResult
      }
    }
    return o1.size.compareTo(o2.size)
  }
}

object ProtoValuePathComponentComparator : Comparator<ProtoValuePathComponent> {
  override fun compare(o1: ProtoValuePathComponent, o2: ProtoValuePathComponent): Int =
    when (o1) {
      is DataConnectPathSegment.Field ->
        when (o2) {
          is DataConnectPathSegment.Field -> o1.field.compareTo(o2.field)
          is DataConnectPathSegment.ListIndex -> -1
        }
      is DataConnectPathSegment.ListIndex ->
        when (o2) {
          is DataConnectPathSegment.Field -> 1
          is DataConnectPathSegment.ListIndex -> o1.index.compareTo(o2.index)
        }
    }
}
