/*
 * Copyright 2026 Google LLC
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

package com.google.firebase.ai.ondevice.interop

/**
 * Represents a single turn or message in a conversation sent to an on-device model.
 *
 * @property parts The ordered list of [Part]s that make up this content.
 * @property role The producer of the content. Defaults to [Role.USER].
 */
public class Content
@JvmOverloads
constructor(public val parts: List<Part>, public val role: Role = Role.USER) {

  /** The role associated with a [Content] turn. */
  public enum class Role {
    /** Content produced by the user, including function responses. */
    USER,

    /** Content produced by the model, including function calls. */
    MODEL,
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is Content) return false
    return role == other.role && parts == other.parts
  }

  override fun hashCode(): Int {
    var result = role.hashCode()
    result = 31 * result + parts.hashCode()
    return result
  }

  override fun toString(): String = "Content(role=$role, parts=$parts)"
}
