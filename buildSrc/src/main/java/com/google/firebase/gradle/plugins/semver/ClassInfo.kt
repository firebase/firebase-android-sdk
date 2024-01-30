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

import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldNode
import org.objectweb.asm.tree.MethodNode

class ClassInfo(node: ClassNode, classNodes: Map<String, ClassNode>) {
  val name: String
  val node: ClassNode
  val fields: Map<String, FieldNode>
  val methods: Map<String, MethodNode>
  val methodsCache: MutableMap<String, Map<String, MethodNode>>

  init {
    this.name = node.name
    this.node = node
    this.fields = getAllFields(node)
    this.methodsCache = mutableMapOf()
    this.methods = getAllMethods(node, classNodes)
  }

  fun getAllFields(node: ClassNode): Map<String, FieldNode> =
    node.fields
      .filterNot {
        AccessDescriptor(it.access).isSynthetic() || UtilityClass.isObfuscatedSymbol(it.name)
      }
      .associate { field -> "${field.name}-${field.desc}" to field }

  fun getAllMethods(node: ClassNode, classNodes: Map<String, ClassNode>): Map<String, MethodNode> =
    getAllStaticMethods(node) + getAllNonStaticMethods(node, classNodes)

  fun getAllStaticMethods(node: ClassNode): Map<String, MethodNode> =
    node.methods
      .filterNot {
        !AccessDescriptor(it.access).isStatic() || AccessDescriptor(it.access).isSynthetic()
      }
      .filterNot { UtilityClass.isObfuscatedSymbol(it.name) || it.name.equals("<clint>") }
      .associate { method -> "${method.name}-${method.desc}" to method }

  fun getAllNonStaticMethods(
    node: ClassNode?,
    classNodes: Map<String, ClassNode>
  ): Map<String, MethodNode> {
    if (node == null) {
      return emptyMap()
    }
    if (methodsCache.containsKey(node.name)) {
      return methodsCache.get(node.name)!!
    }
    val result: MutableMap<String, MethodNode> =
      node.methods
        .filterNot {
          (AccessDescriptor(it.access).isSynthetic() ||
            AccessDescriptor(it.access).isBridge() ||
            AccessDescriptor(it.access).isStatic())
        }
        .filterNot { UtilityClass.isObfuscatedSymbol(it.name) }
        .associate { method -> "${method.name}-${method.desc}" to method }
        as MutableMap<String, MethodNode>

    if (node.superName != null) {
      result.putAll(getAllNonStaticMethods(classNodes.get(node.superName), classNodes))
    }
    if (node.interfaces != null) {
      for (interfaceName in node.interfaces) {
        result.putAll(getAllNonStaticMethods(classNodes.get(interfaceName), classNodes))
      }
    }
    methodsCache.put(node.name, result)
    return result
  }
}
