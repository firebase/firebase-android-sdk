package com.google.firebase.gradle.plugins.semver

/** Encapsulation of a change between two APIs. */
class Delta(
  val className: String,
  val memberName: String,
  val description: String,
  val deltaType: DeltaType,
  val versionDelta: VersionDelta,
)
