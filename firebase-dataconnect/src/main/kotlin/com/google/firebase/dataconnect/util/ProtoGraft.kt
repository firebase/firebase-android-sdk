/*
 * Copyright 2026 Google LLC
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

package com.google.firebase.dataconnect.util

import com.google.firebase.dataconnect.DataConnectPath
import com.google.firebase.dataconnect.DataConnectPathSegment
import com.google.firebase.dataconnect.emptyDataConnectPath
import com.google.firebase.dataconnect.toPathString
import com.google.firebase.dataconnect.util.ProtoUtil.toValueProto
import com.google.protobuf.Struct

/**
 * Holder for "global" functions for grafting values into Proto Struct objects.
 *
 * Technically, these functions _could_ be defined as free functions; however, doing so creates a
 * ProtoStructEncoderKt, ProtoUtilKt, etc. Java class with public visibility, which pollutes the
 * public API. Using an "internal" object, instead, to gather together the top-level functions
 * avoids this public API pollution.
 */
internal object ProtoGraft {

  internal fun Struct.withGraftedInStructs(structsByPath: Map<DataConnectPath, Struct>): Struct =
    graftInValues(this, structsByPath)

  private fun graftInValues(
    originalStruct: Struct,
    structsByPath: Map<DataConnectPath, Struct>,
  ): Struct {
    // Short circuit if nothing to graft.
    if (structsByPath.isEmpty()) {
      return originalStruct
    }

    val rootStructBuilder: Struct.Builder = run {
      val emptyPathStruct = structsByPath[emptyDataConnectPath()]
      if (emptyPathStruct === null) {
        originalStruct.toBuilder()
      } else if (structsByPath.size == 1) {
        // Short circuit if only graft is replacing the entire struct.
        return emptyPathStruct
      } else {
        emptyPathStruct.toBuilder()
      }
    }

    structsByPath.entries
      .filterNot { it.key.isEmpty() }
      .forEach { (fullDestPath, structToInsert) ->
        val destStructPath = fullDestPath.dropLast(1)
        val destKey =
          when (val lastPathSegment = fullDestPath.last()) {
            is DataConnectPathSegment.Field -> lastPathSegment.field
            is DataConnectPathSegment.ListIndex ->
              throw LastInsertPathSegmentNotFieldException(
                "structsByPath contains path=${fullDestPath.toPathString()} whose last path " +
                  "segment is list index ${lastPathSegment.index}, but all paths in structsByPath " +
                  "must have a field as the last path segment, not a list index [qxgass8cvx]"
              )
          }

        val destStructBuilder: Struct.Builder =
          if (destStructPath.isEmpty()) {
            rootStructBuilder
          } else {
            TODO("inserting not into the root struct not yet supported")
          }

        if (destStructBuilder.containsFields(destKey)) {
          throw KeyExistsException(
            "structsByPath contains path=${fullDestPath.toPathString()} which already exists " +
              "with type=${destStructBuilder.getFieldsOrThrow(destKey).kindCase}, " +
              "but expected the key to not exist so that it could be grafted in [z77ec2cznn]"
          )
        }

        rootStructBuilder.putFields(destKey, structToInsert.toValueProto())
      }

    return rootStructBuilder.build()
  }

  class LastInsertPathSegmentNotFieldException(message: String) : Exception(message)

  class FirstInsertPathSegmentNotFieldException(message: String) : Exception(message)

  class KeyExistsException(message: String) : Exception(message)
}
