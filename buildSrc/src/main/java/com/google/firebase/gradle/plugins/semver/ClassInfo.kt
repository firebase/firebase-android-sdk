// Copyright 2023 Google LLC
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

package com.google.firebase.gradle.plugins.semver

import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldNode
import org.objectweb.asm.tree.MethodNode

class ClassInfo(val _node: ClassNode, val _classNodes: Map<String, ClassNode>) {
  val name: String
  val node: ClassNode
  val fields: Map<String, FieldNode>
  val methods: Map<String, MethodNode>
  val methodsCache: MutableMap<String, Map<String, MethodNode>>

  init {
    this.name = _node.name
    this.node = _node
    this.fields = getAllFields(_node)
    this.methodsCache = mutableMapOf()
    this.methods = getAllMethods(_node, _classNodes)
  }

  fun getAllFields(node: ClassNode): Map<String, FieldNode> {
    var fields: MutableMap<String, FieldNode> = mutableMapOf()
    for (field in node.fields) {
      val descriptor = AccessDescriptor(field.access)
      if (descriptor.isSynthetic() || UtilityClass.isObfuscatedSymbol(field.name)) continue
      fields.put("${field.name}-${field.desc}", field)
    }
    return fields
  }

  fun getAllMethods(node: ClassNode, classNodes: Map<String, ClassNode>): Map<String, MethodNode> {
    return getAllStaticMethods(node) + getAllNonStaticMethods(node, classNodes)
  }

  fun getAllStaticMethods(node: ClassNode): Map<String, MethodNode> {
    var staticMethods: MutableMap<String, MethodNode> = mutableMapOf()
    for (method in node.methods) {
      val descriptor = AccessDescriptor(method.access)
      if (!descriptor.isStatic() || descriptor.isSynthetic()) continue
      if (UtilityClass.isObfuscatedSymbol(method.name) || method.name.equals("<clint>")) {
        continue
      }
      staticMethods.put("${method.name}-${method.desc}", method)
    }
    return staticMethods
  }

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
    var result = mutableMapOf<String, MethodNode>()
    for (method in node.methods) {
      var descriptor = AccessDescriptor(method.access)
      if (descriptor.isSynthetic() || descriptor.isBridge() || descriptor.isStatic()) {
        continue
      }
      if (UtilityClass.isObfuscatedSymbol(method.name)) {
        continue
      }
      result.put("${method.name}-${method.desc}", method)
    }
    if (node.superName != null) {
      result =
        (result + getAllNonStaticMethods(classNodes.get(node.superName), classNodes))
          as MutableMap<String, MethodNode>
    }
    if (node.interfaces != null) {
      for (interfaceName in node.interfaces) {
        result =
          (result + getAllNonStaticMethods(classNodes.get(interfaceName), classNodes))
            as MutableMap<String, MethodNode>
      }
    }
    methodsCache.put(node.name, result)
    return result
  }
}
