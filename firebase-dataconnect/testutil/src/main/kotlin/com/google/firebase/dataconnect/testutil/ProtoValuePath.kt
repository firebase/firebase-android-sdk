/*
 * Copyright 2025 Google LLC
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
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

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

typealias MutableProtoValuePath = MutableList<ProtoValuePathComponent>

data class ProtoValuePathPair(val path: ProtoValuePath, val value: Value)

object ProtoValuePathPairPathComparator : Comparator<ProtoValuePathPair> {
  override fun compare(o1: ProtoValuePathPair, o2: ProtoValuePathPair): Int =
    ProtoValuePathComparator.compare(o1.path, o2.path)
}

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
      is ProtoValuePathComponent.StructKey ->
        when (o2) {
          is ProtoValuePathComponent.StructKey -> o1.key.compareTo(o2.key)
          is ProtoValuePathComponent.ListIndex -> -1
        }
      is ProtoValuePathComponent.ListIndex ->
        when (o2) {
          is ProtoValuePathComponent.StructKey -> 1
          is ProtoValuePathComponent.ListIndex -> o1.index.compareTo(o2.index)
        }
    }
}

fun ProtoValuePath.withAppendedListIndex(index: Int): ProtoValuePath =
  withAppendedComponent(ProtoValuePathComponent.ListIndex(index))

fun ProtoValuePath.withAppendedStructKey(key: String): ProtoValuePath =
  withAppendedComponent(ProtoValuePathComponent.StructKey(key))

fun ProtoValuePath.withAppendedComponent(component: ProtoValuePathComponent): ProtoValuePath =
  buildList {
    addAll(this@withAppendedComponent)
    add(component)
  }

fun <T> MutableProtoValuePath.withAppendedListIndex(index: Int, block: () -> T): T =
  withAppendedComponent(ProtoValuePathComponent.ListIndex(index), block)

fun <T> MutableProtoValuePath.withAppendedStructKey(key: String, block: () -> T): T =
  withAppendedComponent(ProtoValuePathComponent.StructKey(key), block)

fun <T> MutableProtoValuePath.withAppendedComponent(
  component: ProtoValuePathComponent,
  block: () -> T
): T {
  val originalSize = size
  add(component)
  try {
    return block()
  } finally {
    val removedComponent = removeLastOrNull()
    check(removedComponent === component)
    check(size == originalSize)
  }
}

@OptIn(ExperimentalContracts::class)
fun ProtoValuePathComponent?.isStructKey(): Boolean {
  contract { returns(true) implies (this@isStructKey is ProtoValuePathComponent.StructKey) }
  return this is ProtoValuePathComponent.StructKey
}

@OptIn(ExperimentalContracts::class)
fun ProtoValuePathComponent?.isListIndex(): Boolean {
  contract { returns(true) implies (this@isListIndex is ProtoValuePathComponent.ListIndex) }
  return this is ProtoValuePathComponent.ListIndex
}

fun ProtoValuePathComponent?.structKeyOrThrow(): String =
  (this as ProtoValuePathComponent.StructKey).key

fun ProtoValuePathComponent?.listIndexOrThrow(): Int =
  (this as ProtoValuePathComponent.ListIndex).index

fun ProtoValuePath.toPathString(): String = buildString { appendPathString(this@toPathString) }

fun StringBuilder.appendPathString(path: ProtoValuePath): StringBuilder = apply {
  path.forEach { pathComponent ->
    when (pathComponent) {
      is ProtoValuePathComponent.StructKey -> {
        if (isNotEmpty()) {
          append('.')
        }
        append('"')
        append(pathComponent.key)
        append('"')
      }
      is ProtoValuePathComponent.ListIndex -> {
        append('[')
        append(pathComponent.index)
        append(']')
      }
    }
  }
}
