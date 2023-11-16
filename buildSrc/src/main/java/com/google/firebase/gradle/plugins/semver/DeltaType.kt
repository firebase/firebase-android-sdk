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

import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldNode
import org.objectweb.asm.tree.MethodNode

enum class DeltaType {
  REMOVED_CLASS {
    override fun getViolations(before: ClassInfo?, after: ClassInfo?): List<Delta> {
      val apiDeltas = mutableListOf<Delta>()
      if (before == null) return apiDeltas
      val beforeAccess = AccessDescriptor(before.node.access)
      if (after == null && !beforeAccess.isPrivate()) {
        apiDeltas.add(
          Delta(
            before.name,
            "",
            String.format("Class %s removed.", before.name),
            REMOVED_CLASS,
            VersionDelta.MAJOR
          )
        )
      }
      return apiDeltas
    }
  },
  REMOVED_METHOD {
    override fun getViolations(before: ClassInfo?, after: ClassInfo?): List<Delta> {
      val beforeMethods =
        getAllMethods(before).filter { (key, value) ->
          val accessDescriptor = AccessDescriptor(value.access)
          !hasNonPublicMethodSignature(value) && !accessDescriptor.isPrivate()
        }
      val afterMethods =
        getAllMethods(after).filter { (key, value) ->
          val accessDescriptor = AccessDescriptor(value.access)
          !hasNonPublicMethodSignature(value) && !accessDescriptor.isPrivate()
        }
      return (beforeMethods.keys subtract afterMethods.keys).map {
        val method = beforeMethods.get(it)
        Delta(
          before!!.name,
          method!!.name,
          String.format(
            "Method %s on class %s was removed.",
            getMethodSignature(method),
            before.name
          ),
          REMOVED_METHOD,
          VersionDelta.MAJOR
        )
      }
    }
  },
  REMOVED_FIELD {
    override fun getViolations(before: ClassInfo?, after: ClassInfo?): List<Delta> {
      val beforeFields =
        getAllFields(before).filter { (key, value) ->
          val accessDescriptor = AccessDescriptor(value.access)
          !hasNonPublicFieldSignature(value) && !accessDescriptor.isPrivate()
        }
      val afterFields =
        getAllFields(after).filter { (key, value) ->
          val accessDescriptor = AccessDescriptor(value.access)
          !hasNonPublicFieldSignature(value) && !accessDescriptor.isPrivate()
        }
      return (beforeFields.keys subtract afterFields.keys).map {
        val field = beforeFields.get(it)
        Delta(
          before!!.name,
          field!!.name,
          String.format("Field %s on class %s was removed.", field.name, before.name),
          REMOVED_FIELD,
          VersionDelta.MAJOR
        )
      }
    }
  },
  REDUCED_VISIBILITY {
    override fun getViolations(before: ClassInfo?, after: ClassInfo?): List<Delta> {
      if (before == null || after == null) return emptyList()
      val allBeforeFields = getAllFields(before)
      val allAfterFields = getAllFields(after)
      val allBeforeMethods = getAllMethods(before)
      val allAfterMethods = getAllMethods(after)
      val publicBeforeFields =
        allBeforeFields.filter { (key, value) ->
          val access = AccessDescriptor(value.access)
          !hasNonPublicFieldSignature(value) && allAfterFields.containsKey(key) && access.isPublic()
        }
      val protectedBeforeFields =
        allBeforeFields.filter { (key, value) ->
          val access = AccessDescriptor(value.access)
          !hasNonPublicFieldSignature(value) &&
            allAfterFields.containsKey(key) &&
            access.isProtected()
        }
      val nonPublicFields =
        allAfterFields.filter { (key, value) ->
          val access = AccessDescriptor(value.access)
          !hasNonPublicFieldSignature(value) &&
            allBeforeFields.containsKey(key) &&
            !access.isPublic()
        }
      val privateFields =
        allAfterFields.filter { (key, value) ->
          val access = AccessDescriptor(value.access)
          !hasNonPublicFieldSignature(value) &&
            allBeforeFields.containsKey(key) &&
            access.isPrivate()
        }
      val publicBeforeMethods =
        allBeforeMethods.filter { (key, value) ->
          val access = AccessDescriptor(value.access)
          !hasNonPublicMethodSignature(value) &&
            allAfterMethods.containsKey(key) &&
            access.isPublic()
        }
      val protectedBeforeMethods =
        allBeforeMethods.filter { (key, value) ->
          val access = AccessDescriptor(value.access)
          !hasNonPublicMethodSignature(value) &&
            allAfterMethods.containsKey(key) &&
            access.isProtected()
        }
      val nonPublicMethods =
        allAfterMethods.filter { (key, value) ->
          val access = AccessDescriptor(value.access)
          !hasNonPublicMethodSignature(value) &&
            allBeforeMethods.containsKey(key) &&
            !access.isPublic()
        }
      val privateMethods =
        allAfterMethods.filter { (key, value) ->
          val access = AccessDescriptor(value.access)
          !hasNonPublicMethodSignature(value) &&
            allBeforeMethods.containsKey(key) &&
            access.isPrivate()
        }
      val apiDeltas = mutableListOf<Delta>()
      val beforeAccess = AccessDescriptor(before.node.access)
      val afterAccess = AccessDescriptor(after.node.access)
      if (
        (beforeAccess.isPublic() && !afterAccess.isPublic()) ||
          (beforeAccess.isProtected() && afterAccess.isPrivate())
      ) {
        apiDeltas.add(
          Delta(
            after.name,
            "",
            String.format("Class %s has reduced visibility.", after.name),
            REDUCED_VISIBILITY,
            VersionDelta.MAJOR
          )
        )
      }

      ((publicBeforeMethods.keys intersect nonPublicMethods.keys) union
          (protectedBeforeMethods.keys intersect privateMethods.keys))
        .forEach {
          val method =
            if (allAfterMethods.get(it) != null) allAfterMethods.get(it)
            else allBeforeMethods.get(it)
          apiDeltas.add(
            Delta(
              after.name,
              method!!.name,
              String.format(
                "Method %s on class %s has reduced its visiblity.",
                getMethodSignature(method),
                after.name
              ),
              REDUCED_VISIBILITY,
              VersionDelta.MAJOR
            )
          )
        }

      ((publicBeforeFields.keys intersect nonPublicFields.keys) union
          (protectedBeforeFields.keys intersect privateFields.keys))
        .forEach {
          val field =
            if (allAfterFields.get(it) != null) allAfterFields.get(it) else allBeforeFields.get(it)
          apiDeltas.add(
            Delta(
              after.name,
              field!!.name,
              String.format(
                "Field %s on class %s has reduced its visiblity.",
                field.name,
                after.name
              ),
              REDUCED_VISIBILITY,
              VersionDelta.MAJOR
            )
          )
        }
      return apiDeltas
    }
  },
  CHANGED_TO_STATIC {
    override fun getViolations(before: ClassInfo?, after: ClassInfo?): List<Delta> {
      val allBeforeMethods = getAllMethods(before)
      val allAfterMethods = getAllMethods(after)
      val afterStaticMethods =
        allAfterMethods.filter { (key, value) ->
          val access = AccessDescriptor(value.access)
          !hasNonPublicMethodSignature(value) &&
            allBeforeMethods.containsKey(key) &&
            access.isStatic()
        }
      val beforeStaticMethods =
        allBeforeMethods.filter { (key, value) ->
          val access = AccessDescriptor(value.access)
          !hasNonPublicMethodSignature(value) &&
            allBeforeMethods.containsKey(key) &&
            access.isStatic()
        }
      return (afterStaticMethods.keys subtract beforeStaticMethods.keys).map {
        val method = allAfterMethods.get(it)
        Delta(
          after!!.name,
          method!!.name,
          String.format("Method %s on class %s became static.", getMethodSignature(method), after),
          CHANGED_TO_STATIC,
          VersionDelta.MAJOR
        )
      }
    }
  },
  NEW_EXCEPTIONS {
    override fun getViolations(before: ClassInfo?, after: ClassInfo?): List<Delta> {
      val allBeforeMethods = getAllMethods(before)
      val allAfterMethods = getAllMethods(after)
      val apiDeltas = mutableListOf<Delta>()
      (allAfterMethods.keys intersect allBeforeMethods.keys).forEach {
        val afterMethod = allAfterMethods.get(it)
        val beforeMethod = allBeforeMethods.get(it)
        if (!beforeMethod!!.exceptions.containsAll(afterMethod!!.exceptions)) {
          apiDeltas.add(
            Delta(
              after!!.name,
              afterMethod!!.name,
              String.format(
                "Method %s on class %s throws new exceptions.",
                getMethodSignature(afterMethod),
                after.name
              ),
              NEW_EXCEPTIONS,
              VersionDelta.MINOR
            )
          )
        }
      }
      return apiDeltas
    }
  },
  DELETE_EXCEPTIONS {
    override fun getViolations(before: ClassInfo?, after: ClassInfo?): List<Delta> {
      val allBeforeMethods = getAllMethods(before)
      val allAfterMethods = getAllMethods(after)
      val apiDeltas = mutableListOf<Delta>()
      (allAfterMethods.keys intersect allBeforeMethods.keys).forEach {
        val afterMethod = allAfterMethods.get(it)
        val beforeMethod = allBeforeMethods.get(it)
        if (!afterMethod!!.exceptions.containsAll(beforeMethod!!.exceptions)) {
          apiDeltas.add(
            Delta(
              after!!.name,
              afterMethod!!.name,
              String.format(
                "Method %s on class %s throws fewer exceptions.",
                getMethodSignature(afterMethod),
                after.name
              ),
              DELETE_EXCEPTIONS,
              VersionDelta.MAJOR
            )
          )
        }
      }
      return apiDeltas
    }
  },
  CLASS_MADE_ABSTRACT {
    override fun getViolations(before: ClassInfo?, after: ClassInfo?): List<Delta> {
      if (before == null || after == null) return emptyList()
      val apiDeltas = mutableListOf<Delta>()
      if (
        AccessDescriptor(after.node.access).isAbstract() &&
          !AccessDescriptor(before.node.access).isAbstract()
      ) {
        apiDeltas.add(
          Delta(
            after.name,
            "",
            String.format("Class %s made abstract.", after.name),
            CLASS_MADE_ABSTRACT,
            VersionDelta.MAJOR
          )
        )
      }
      return apiDeltas
    }
  },
  METHOD_MADE_ABSTRACT {
    override fun getViolations(before: ClassInfo?, after: ClassInfo?): List<Delta> {
      val allBeforeMethods = getAllMethods(before)
      val allAfterMethods = getAllMethods(after)
      val apiDeltas = mutableListOf<Delta>()
      (allAfterMethods.keys intersect allBeforeMethods.keys).forEach {
        val afterMethod = allAfterMethods.get(it)
        val beforeMethod = allBeforeMethods.get(it)

        if (
          AccessDescriptor(afterMethod!!.access).isAbstract() &&
            !AccessDescriptor(beforeMethod!!.access).isAbstract()
        ) {
          apiDeltas.add(
            Delta(
              after!!.name,
              afterMethod!!.name,
              String.format(
                "Method %s on class %s became abstract.",
                getMethodSignature(afterMethod),
                after.name
              ),
              METHOD_MADE_ABSTRACT,
              VersionDelta.MAJOR
            )
          )
        }
      }
      return apiDeltas
    }
  },
  METHOD_MADE_FINAL {
    override fun getViolations(before: ClassInfo?, after: ClassInfo?): List<Delta> {
      val allBeforeMethods = getAllMethods(before)
      val allAfterMethods = getAllMethods(after)
      val apiDeltas = mutableListOf<Delta>()
      (allAfterMethods.keys intersect allBeforeMethods.keys).forEach {
        val afterMethod = allAfterMethods.get(it)
        val beforeMethod = allBeforeMethods.get(it)

        if (
          AccessDescriptor(afterMethod!!.access).isFinal() &&
            !AccessDescriptor(beforeMethod!!.access).isFinal()
        ) {
          apiDeltas.add(
            Delta(
              after!!.name,
              afterMethod!!.name,
              String.format(
                "Method %s on class %s became final.",
                getMethodSignature(afterMethod),
                after.name
              ),
              METHOD_MADE_FINAL,
              VersionDelta.MAJOR
            )
          )
        }
      }
      return apiDeltas
    }
  },
  CLASS_MADE_FINAL {
    override fun getViolations(before: ClassInfo?, after: ClassInfo?): List<Delta> {
      if (before == null || after == null) return emptyList()
      val apiDeltas = mutableListOf<Delta>()
      if (
        AccessDescriptor(after.node.access).isFinal() &&
          !AccessDescriptor(before.node.access).isFinal()
      ) {
        apiDeltas.add(
          Delta(
            after.name,
            "",
            String.format("Class %s made abstract.", after.name),
            CLASS_MADE_FINAL,
            VersionDelta.MAJOR
          )
        )
      }
      return apiDeltas
    }
  },
  ADDED_CLASS {
    override fun getViolations(before: ClassInfo?, after: ClassInfo?): List<Delta> {
      val apiDeltas = mutableListOf<Delta>()
      if (after == null) return apiDeltas
      val afterAccess = AccessDescriptor(after.node.access)
      if (before == null && !afterAccess.isPrivate()) {
        apiDeltas.add(
          Delta(
            after.name,
            "",
            String.format("Class %s added.", after.name),
            ADDED_CLASS,
            VersionDelta.MINOR
          )
        )
      }
      return apiDeltas
    }
  },
  ADDED_METHOD {
    override fun getViolations(before: ClassInfo?, after: ClassInfo?): List<Delta> {
      val beforeMethods =
        getAllMethods(before).filter { (key, value) ->
          val accessDescriptor = AccessDescriptor(value.access)
          !hasNonPublicMethodSignature(value) && !accessDescriptor.isPrivate()
        }
      val afterMethods =
        getAllMethods(after).filter { (key, value) ->
          val accessDescriptor = AccessDescriptor(value.access)
          !hasNonPublicMethodSignature(value) && !accessDescriptor.isPrivate()
        }
      return (afterMethods.keys subtract beforeMethods.keys).map {
        val method = afterMethods.get(it)
        Delta(
          after!!.name,
          method!!.name,
          String.format("Method %s on class %s was added.", getMethodSignature(method), after.name),
          ADDED_METHOD,
          VersionDelta.MINOR
        )
      }
    }
  },
  ADDED_FIELD {
    override fun getViolations(before: ClassInfo?, after: ClassInfo?): List<Delta> {
      val beforeFields =
        getAllFields(before).filter { (key, value) ->
          val accessDescriptor = AccessDescriptor(value.access)
          !hasNonPublicFieldSignature(value) && !accessDescriptor.isPrivate()
        }
      val afterFields =
        getAllFields(after).filter { (key, value) ->
          val accessDescriptor = AccessDescriptor(value.access)
          !hasNonPublicFieldSignature(value) && !accessDescriptor.isPrivate()
        }

      return (afterFields.keys subtract beforeFields.keys).map {
        val field = afterFields[it]
        Delta(
          after!!.name,
          field!!.name,
          String.format("Field %s on class %s was added.", field.name, after.name),
          ADDED_FIELD,
          VersionDelta.MINOR
        )
      }
    }
  },
  INCREASED_VISIBILITY {
    override fun getViolations(before: ClassInfo?, after: ClassInfo?): List<Delta> {
      if (before == null || after == null) return emptyList()
      val allBeforeFields = getAllFields(before)
      val allAfterFields = getAllFields(after)
      val allBeforeMethods = getAllMethods(before)
      val allAfterMethods = getAllMethods(after)
      val publicAfterFields =
        allAfterFields.filter { (key, value) ->
          val access = AccessDescriptor(value.access)
          !hasNonPublicFieldSignature(value) &&
            allBeforeFields.containsKey(key) &&
            access.isPublic()
        }
      val protectedAfterFields =
        allAfterFields.filter { (key, value) ->
          val access = AccessDescriptor(value.access)
          !hasNonPublicFieldSignature(value) &&
            allBeforeFields.containsKey(key) &&
            access.isProtected()
        }
      val nonPublicFields =
        allBeforeFields.filter { (key, value) ->
          val access = AccessDescriptor(value.access)
          !hasNonPublicFieldSignature(value) &&
            allAfterFields.containsKey(key) &&
            !access.isPublic()
        }
      val privateFields =
        allBeforeFields.filter { (key, value) ->
          val access = AccessDescriptor(value.access)
          !hasNonPublicFieldSignature(value) &&
            allAfterFields.containsKey(key) &&
            access.isPrivate()
        }
      val publicAfterMethods =
        allAfterMethods.filter { (key, value) ->
          val access = AccessDescriptor(value.access)
          !hasNonPublicMethodSignature(value) &&
            allBeforeMethods.containsKey(key) &&
            access.isPublic()
        }
      val protectedAfterMethods =
        allAfterMethods.filter { (key, value) ->
          val access = AccessDescriptor(value.access)
          !hasNonPublicMethodSignature(value) &&
            allBeforeMethods.containsKey(key) &&
            access.isProtected()
        }
      val nonPublicMethods =
        allBeforeMethods.filter { (key, value) ->
          val access = AccessDescriptor(value.access)
          !hasNonPublicMethodSignature(value) &&
            allAfterMethods.containsKey(key) &&
            !access.isPublic()
        }
      val privateMethods =
        allBeforeMethods.filter { (key, value) ->
          val access = AccessDescriptor(value.access)
          !hasNonPublicMethodSignature(value) &&
            allAfterMethods.containsKey(key) &&
            access.isPrivate()
        }
      val apiDeltas = mutableListOf<Delta>()
      val beforeAccess = AccessDescriptor(before.node.access)
      val afterAccess = AccessDescriptor(after.node.access)
      if (
        (afterAccess.isPublic() && !beforeAccess.isPublic()) ||
          (afterAccess.isProtected() && beforeAccess.isPrivate())
      ) {
        apiDeltas.add(
          Delta(
            after.name,
            "",
            String.format("Class %s has increased visibility.", after.name),
            INCREASED_VISIBILITY,
            VersionDelta.MINOR
          )
        )
      }

      ((publicAfterMethods.keys intersect nonPublicMethods.keys) union
          ((protectedAfterMethods.keys) intersect privateMethods.keys))
        .forEach {
          val method = allAfterMethods.get(it)
          apiDeltas.add(
            Delta(
              after.name,
              method!!.name,
              String.format(
                "Method %s on class %s has increased its visiblity.",
                getMethodSignature(method),
                after.name
              ),
              INCREASED_VISIBILITY,
              VersionDelta.MINOR
            )
          )
        }

      ((publicAfterFields.keys intersect nonPublicFields.keys) union
          (protectedAfterFields.keys intersect privateFields.keys))
        .forEach {
          val field = allAfterFields.get(it)
          apiDeltas.add(
            Delta(
              after.name,
              field!!.name,
              String.format(
                "Field %s on class %s has reduced its visiblity.",
                field.name,
                after.name
              ),
              INCREASED_VISIBILITY,
              VersionDelta.MINOR
            )
          )
        }
      return apiDeltas
    }
  },
  CHANGED_FROM_STATIC {
    override fun getViolations(before: ClassInfo?, after: ClassInfo?): List<Delta> {
      val allBeforeMethods = getAllMethods(before)
      val allAfterMethods = getAllMethods(after)
      val afterStaticMethods =
        allAfterMethods.filter { (key, value) ->
          val access = AccessDescriptor(value.access)
          !hasNonPublicMethodSignature(value) &&
            allBeforeMethods.containsKey(key) &&
            access.isStatic()
        }
      val beforeStaticMethods =
        allBeforeMethods.filter { (key, value) ->
          val access = AccessDescriptor(value.access)
          !hasNonPublicMethodSignature(value) &&
            allBeforeMethods.containsKey(key) &&
            access.isStatic()
        }

      return (beforeStaticMethods.keys subtract afterStaticMethods.keys).map {
        val method = beforeStaticMethods.get(it)
        Delta(
          after!!.name,
          method!!.name,
          String.format("Method %s on class %s removed static.", getMethodSignature(method), after),
          CHANGED_FROM_STATIC,
          VersionDelta.MINOR
        )
      }
    }
  };

  abstract fun getViolations(before: ClassInfo?, after: ClassInfo?): List<Delta>

  fun getAllFields(classInfo: ClassInfo?): Map<String, FieldNode> {
    if (classInfo == null) return emptyMap()
    return classInfo.fields
  }

  fun getAllMethods(classInfo: ClassInfo?): Map<String, MethodNode> {
    if (classInfo == null) return emptyMap()
    return classInfo.methods
  }

  fun getMethodSignature(methodNode: MethodNode): String {
    val parameterTypes = Type.getArgumentTypes(methodNode.desc)
    var parameterClasses = mutableListOf<String>()
    for (type in parameterTypes) {
      parameterClasses.add(type.getClassName())
    }
    return String.format(
      "%s %s %s(%s)",
      AccessDescriptor(methodNode.access).getVerboseDescription(),
      Type.getReturnType(methodNode.desc).className,
      methodNode.name,
      parameterClasses.joinToString(", ")
    )
  }

  fun hasNonPublicMethodSignature(methodNode: MethodNode): Boolean {
    if (
      UtilityClass.isObfuscatedSymbol(
        getUnqualifiedClassname(Type.getReturnType(methodNode.desc).getClassName())
      )
    ) {
      return true
    }

    val parameterTypes = Type.getArgumentTypes(methodNode.desc)
    for (type in parameterTypes) {
      if (UtilityClass.isObfuscatedSymbol(getUnqualifiedClassname(type.getClassName()))) {
        return true
      }
    }
    return false
  }
  /** Returns true if the class is local or anonymous. */
  fun isLocalOrAnonymous(classNode: ClassNode): Boolean {
    // JVMS 4.7.7 says a class has an EnclosingMethod attribute iff it is local or anonymous.
    // ASM sets the "enclosing class" only if an EnclosingMethod attribute is present, so this
    // this test will not include named inner classes even though they have enclosing classes.
    return classNode.outerClass != null
  }

  fun getUnqualifiedClassname(classNodeName: String): String {
    // Class names may be "/" or "." separated depending on context. Normalize to "/"
    val normalizedPath = classNodeName.replace(".", "/")
    val withoutPackage = normalizedPath.substring(normalizedPath.lastIndexOf('/') + 1)
    return withoutPackage.substring(withoutPackage.lastIndexOf('$') + 1)
  }
  fun hasNonPublicFieldSignature(fieldNode: FieldNode): Boolean {
    val fieldType = Type.getType(fieldNode.desc)
    return UtilityClass.isObfuscatedSymbol(getUnqualifiedClassname(fieldType.getClassName()))
  }
}
