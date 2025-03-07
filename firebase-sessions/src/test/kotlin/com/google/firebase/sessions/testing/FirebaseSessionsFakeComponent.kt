package com.google.firebase.sessions.testing

import com.google.firebase.Firebase
import com.google.firebase.app
import com.google.firebase.sessions.FirebaseSessions
import com.google.firebase.sessions.FirebaseSessionsComponent
import com.google.firebase.sessions.SessionDatastore
import com.google.firebase.sessions.SessionFirelogPublisher
import com.google.firebase.sessions.SessionGenerator
import com.google.firebase.sessions.settings.SessionsSettings

/** Bridge between FirebaseSessionsComponent and FirebaseSessionsFakeRegistrar. */
internal class FirebaseSessionsFakeComponent : FirebaseSessionsComponent {
  // TODO(mrober): Move tests to use Dagger for DI.

  override val firebaseSessions: FirebaseSessions
    get() = Firebase.app[FirebaseSessions::class.java]

  override val sessionDatastore: SessionDatastore
    get() = Firebase.app[SessionDatastore::class.java]

  override val sessionFirelogPublisher: SessionFirelogPublisher
    get() = Firebase.app[SessionFirelogPublisher::class.java]

  override val sessionGenerator: SessionGenerator
    get() = Firebase.app[SessionGenerator::class.java]

  override val sessionsSettings: SessionsSettings
    get() = Firebase.app[SessionsSettings::class.java]
}
