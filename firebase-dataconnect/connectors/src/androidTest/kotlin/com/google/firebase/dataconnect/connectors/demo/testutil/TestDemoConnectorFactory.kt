package com.google.firebase.dataconnect.connectors.demo.testutil

import com.google.firebase.FirebaseApp
import com.google.firebase.dataconnect.DataConnectSettings
import com.google.firebase.dataconnect.FirebaseDataConnect
import com.google.firebase.dataconnect.connectors.demo.DemoConnector
import com.google.firebase.dataconnect.connectors.demo.getInstance
import com.google.firebase.dataconnect.connectors.testutil.TestConnectorFactory
import com.google.firebase.dataconnect.testutil.TestDataConnectFactory
import com.google.firebase.dataconnect.testutil.TestFirebaseAppFactory

/**
 * A JUnit test rule that creates instances of [DemoConnector] for use during testing, and closes
 * their underlying [FirebaseDataConnect] instances upon test completion.
 */
class TestDemoConnectorFactory(
  firebaseAppFactory: TestFirebaseAppFactory,
  dataConnectFactory: TestDataConnectFactory
) : TestConnectorFactory<DemoConnector>(firebaseAppFactory, dataConnectFactory) {
  override fun createConnector(firebaseApp: FirebaseApp, settings: DataConnectSettings) =
    DemoConnector.getInstance(firebaseApp, settings)
}
