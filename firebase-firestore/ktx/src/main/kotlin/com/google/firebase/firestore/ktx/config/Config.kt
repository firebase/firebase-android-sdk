package com.google.firebase.firestore.ktx.config

import com.google.firebase.emulators.EmulatedServiceSettings
import com.google.firebase.ktx.config.ConfigValue
import com.google.firebase.ktx.config.ConfigurationDsl
import com.google.firebase.ktx.config.internalGetForConfigure

interface FirestoreDsl {
  fun useEmulator(host: String, port: Int)

  var isNetworkEnabled: Boolean
}

internal class FirestoreConfiguration(
  var emulatedSettings: EmulatedServiceSettings? = null,
  override var isNetworkEnabled: Boolean = true
) : FirestoreDsl, ConfigValue {
  override fun useEmulator(host: String, port: Int) {
    emulatedSettings = EmulatedServiceSettings(host, port)
  }
}

fun ConfigurationDsl.firestore(block: FirestoreDsl.() -> Unit) {
  internalGetForConfigure(this, FirestoreConfiguration::class.java) { FirestoreConfiguration() }
    .also { it.block() }
}
