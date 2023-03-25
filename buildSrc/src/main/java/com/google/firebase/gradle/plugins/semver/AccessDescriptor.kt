package com.google.firebase.gradle.plugins.semver

import org.objectweb.asm.Opcodes

/**
 * Convenience class that helps avoid confusing (if more performant) bitwise checks against {@link
 * Opcodes}
 */
class AccessDescriptor(private val access: Int) {
  fun isProtected(): Boolean {
    return accessIs(Opcodes.ACC_PROTECTED)
  }
  fun isPublic(): Boolean {
    return accessIs(Opcodes.ACC_PUBLIC)
  }
  fun isStatic(): Boolean {
    return accessIs(Opcodes.ACC_STATIC)
  }
  fun isSynthetic(): Boolean {
    return accessIs(Opcodes.ACC_SYNTHETIC)
  }
  fun isBridge(): Boolean {
    return accessIs(Opcodes.ACC_BRIDGE)
  }
  fun isAbstract(): Boolean {
    return accessIs(Opcodes.ACC_ABSTRACT)
  }

  fun isFinal(): Boolean {
    return accessIs(Opcodes.ACC_FINAL)
  }

  fun isPrivate(): Boolean {
    return !this.isProtected() && !this.isPublic()
  }

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
  fun accessIs(opcode: Int): Boolean {
    return (access and opcode) != 0
  }
}
