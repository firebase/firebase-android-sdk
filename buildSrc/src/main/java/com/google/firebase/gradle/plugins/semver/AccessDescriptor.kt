/*
 * Copyright 2023 Google LLC
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

package com.google.firebase.gradle.plugins.semver

import org.objectweb.asm.Opcodes

/**
 * Convenience class that helps avoid confusing (if more performant) bitwise checks against {@link
 * Opcodes}
 */
class AccessDescriptor(private val access: Int) {
  fun isProtected(): Boolean = accessIs(Opcodes.ACC_PROTECTED)

  fun isPublic(): Boolean = accessIs(Opcodes.ACC_PUBLIC)

  fun isStatic(): Boolean = accessIs(Opcodes.ACC_STATIC)

  fun isSynthetic(): Boolean = accessIs(Opcodes.ACC_SYNTHETIC)

  fun isBridge(): Boolean = accessIs(Opcodes.ACC_BRIDGE)

  fun isAbstract(): Boolean = accessIs(Opcodes.ACC_ABSTRACT)

  fun isFinal(): Boolean = accessIs(Opcodes.ACC_FINAL)

  fun isPrivate(): Boolean = !this.isProtected() && !this.isPublic()

  fun getVerboseDescription(): String {
    val outputStringList = mutableListOf<String>()
    if (this.isPublic()) {
      outputStringList.add("public")
    }
    if (this.isPrivate()) {
      outputStringList.add("private")
    }
    if (this.isProtected()) {
      outputStringList.add("protected")
    }
    if (this.isStatic()) {
      outputStringList.add("static")
    }
    if (this.isFinal()) {
      outputStringList.add("final")
    }
    if (this.isAbstract()) {
      outputStringList.add("abstract")
    }
    return outputStringList.joinToString(" ")
  }
  /** Returns true if the given access modifier matches the given opcode. */
  fun accessIs(opcode: Int): Boolean = (access and opcode) != 0
}
