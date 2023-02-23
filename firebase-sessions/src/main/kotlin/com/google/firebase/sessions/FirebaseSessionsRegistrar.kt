package com.google.firebase.sessions

import androidx.annotation.Keep
import com.google.firebase.components.Component
import com.google.firebase.components.ComponentRegistrar
import com.google.firebase.platforminfo.LibraryVersionComponent

/**
 * [ComponentRegistrar] for setting up [FirebaseSessions].
 *
 * @hide
 */
@Keep
internal class FirebaseSessionsRegistrar : ComponentRegistrar {
  override fun getComponents() =
    listOf(
      Component.builder(FirebaseSessions::class.java)
        .name(LIBRARY_NAME)
        .factory { FirebaseSessions() }
        .build(),
      LibraryVersionComponent.create(LIBRARY_NAME, BuildConfig.VERSION_NAME)
    )

  companion object {
    private const val LIBRARY_NAME = "fire-ses"
  }
}
