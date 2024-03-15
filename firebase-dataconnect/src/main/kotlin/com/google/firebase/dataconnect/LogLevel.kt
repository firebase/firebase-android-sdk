package com.google.firebase.dataconnect

public enum class LogLevel {
  DEBUG,
  WARN,
  NONE,
}

public var FirebaseDataConnect.Companion.logLevel: LogLevel
  get() = com.google.firebase.dataconnect.core.logLevel
  set(newLogLevel) {
    com.google.firebase.dataconnect.core.logLevel = newLogLevel
  }
