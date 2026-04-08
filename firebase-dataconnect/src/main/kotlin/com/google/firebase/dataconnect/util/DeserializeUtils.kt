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

import com.google.firebase.dataconnect.DataConnectPathSegment
import com.google.firebase.dataconnect.core.DataConnectOperationFailureResponseImpl
import com.google.firebase.dataconnect.util.ProtoUtil.toCompactString
import com.google.protobuf.ListValue
import com.google.protobuf.Value
import google.firebase.dataconnect.proto.GraphqlError

internal object DeserializeUtils {

  fun GraphqlError.toErrorInfoImpl() =
    DataConnectOperationFailureResponseImpl.ErrorInfoImpl(
      message = message,
      path = path.toPathSegment(),
    )

  private fun ListValue.toPathSegment() =
    valuesList.map {
      when (it.kindCase) {
        Value.KindCase.STRING_VALUE -> DataConnectPathSegment.Field(it.stringValue)
        Value.KindCase.NUMBER_VALUE -> DataConnectPathSegment.ListIndex(it.numberValue.toInt())
        // The other cases are expected to never occur; however, implement some logic for them
        // to avoid things like throwing exceptions in those cases.
        else -> DataConnectPathSegment.Field(it.toCompactString())
      }
    }
}
