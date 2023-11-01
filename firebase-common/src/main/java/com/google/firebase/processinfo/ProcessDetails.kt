package com.google.firebase.processinfo

/**
 * Container for information about the process
 */
data class ProcessDetails(
  val processName: String,
  val pid: Int,
  val importance: Int,
  val isDefault: Boolean,
)
