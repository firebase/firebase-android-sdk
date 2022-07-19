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
package com.google.firebase.firestore

import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import kotlinx.serialization.SerialInfo

/**
 * Annotation used to mark a timestamp field to be populated with a server timestamp. If a POJO
 * being written contains `null` for a @ServerTimestamp-annotated field, it will be replaced with a
 * server-generated timestamp.
 */
@SerialInfo
@Retention(RetentionPolicy.RUNTIME)
@Target(
// TODO: revert this file back to Java or find out a method to share this file between Java and Ktx.
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY_GETTER,
    AnnotationTarget.PROPERTY_SETTER,
    AnnotationTarget.FIELD

)
annotation class ServerTimestamp
