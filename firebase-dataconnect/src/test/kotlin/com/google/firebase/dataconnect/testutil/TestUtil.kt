package com.google.firebase.dataconnect.testutil

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

fun FirebaseDataConnect.Companion.newTestInstance() =
  FirebaseDataConnect(
    context = RuntimeEnvironment.getApplication(),
    app = mock(FirebaseApp::class.java),
    projectId = Random.nextAlphanumericString(),
    config =
      ConnectorConfig(
        connector = "Connector" + Random.nextAlphanumericString(),
        location = "Location" + Random.nextAlphanumericString(),
        service = "Service" + Random.nextAlphanumericString(),
      ),
    blockingExecutor = MoreExecutors.directExecutor(),
    nonBlockingExecutor = MoreExecutors.directExecutor(),
    creator = mock(FirebaseDataConnectFactory::class.java),
    settings = DataConnectSettings(host = Random.nextAlphanumericString())
  )
