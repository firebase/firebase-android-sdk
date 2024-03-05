package com.google.firebase.dataconnect.testutil

import android.content.Context
import com.google.common.util.concurrent.MoreExecutors
import com.google.firebase.FirebaseApp
import com.google.firebase.dataconnect.ConnectorConfig
import com.google.firebase.dataconnect.DataConnectSettings
import com.google.firebase.dataconnect.FirebaseDataConnect
import com.google.firebase.dataconnect.FirebaseDataConnectFactory
import com.google.firebase.dataconnect.nextAlphanumericString
import kotlin.random.Random
import org.mockito.Mockito.mock
import org.robolectric.RuntimeEnvironment

fun FirebaseDataConnect.Companion.newTestInstance(): FirebaseDataConnect {
  val context: Context = RuntimeEnvironment.getApplication()
  val firebaseApp = mock(FirebaseApp::class.java)
  val blockingExecutor = MoreExecutors.directExecutor()
  val nonBlockingExecutor = MoreExecutors.directExecutor()

  return FirebaseDataConnect(
    context = context,
    app = firebaseApp,
    projectId = Random.nextAlphanumericString(),
    config =
      ConnectorConfig(
        connector = "Connector" + Random.nextAlphanumericString(),
        location = "Location" + Random.nextAlphanumericString(),
        service = "Service" + Random.nextAlphanumericString(),
      ),
    blockingExecutor = blockingExecutor,
    nonBlockingExecutor = nonBlockingExecutor,
    creator =
      FirebaseDataConnectFactory(
        context = context,
        firebaseApp = firebaseApp,
        blockingExecutor = blockingExecutor,
        nonBlockingExecutor = nonBlockingExecutor,
      ),
    settings = DataConnectSettings(host = Random.nextAlphanumericString())
  )
}
