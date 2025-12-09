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

import com.google.protobuf.Value
import java.util.Objects

sealed interface ProtoValuePathComponent {

  class StructKey(val key: String) : ProtoValuePathComponent {
    override fun equals(other: Any?) = other is StructKey && other.key == key
    override fun hashCode() = Objects.hash(StructKey::class.java, key)
    override fun toString() = "StructKey(\"$key\")"
  }

  class ListIndex(val index: Int) : ProtoValuePathComponent {
    override fun equals(other: Any?) = other is ListIndex && other.index == index
    override fun hashCode() = Objects.hash(ListIndex::class.java, index)
    override fun toString() = "ListIndex($index)"
  }
}

typealias ProtoValuePath = List<ProtoValuePathComponent>

data class ProtoValuePathPair(val path: ProtoValuePath, val value: Value)

fun ProtoValuePath.withAppendedListIndex(index: Int): ProtoValuePath =
  withAppendedComponent(ProtoValuePathComponent.ListIndex(index))

fun ProtoValuePath.withAppendedStructKey(key: String): ProtoValuePath =
  withAppendedComponent(ProtoValuePathComponent.StructKey(key))

fun ProtoValuePath.withAppendedComponent(component: ProtoValuePathComponent): ProtoValuePath =
  buildList {
    addAll(this@withAppendedComponent)
    add(component)
  }
