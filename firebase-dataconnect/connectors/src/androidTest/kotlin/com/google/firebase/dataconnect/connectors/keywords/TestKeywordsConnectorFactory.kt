package com.google.firebase.dataconnect.connectors.keywords

import com.google.firebase.FirebaseApp
import com.google.firebase.dataconnect.DataConnectSettings
import com.google.firebase.dataconnect.FirebaseDataConnect
import com.google.firebase.dataconnect.connectors.testutil.TestConnectorFactory
import com.google.firebase.dataconnect.connectors.`typealias`.KeywordsConnector
import com.google.firebase.dataconnect.connectors.`typealias`.getInstance
import com.google.firebase.dataconnect.testutil.TestDataConnectFactory
import com.google.firebase.dataconnect.testutil.TestFirebaseAppFactory

/**
 * A JUnit test rule that creates instances of [KeywordsConnector] for use during testing, and
 * closes their underlying [FirebaseDataConnect] instances upon test completion.
 */
class TestKeywordsConnectorFactory(
  firebaseAppFactory: TestFirebaseAppFactory,
  dataConnectFactory: TestDataConnectFactory
) : TestConnectorFactory<KeywordsConnector>(firebaseAppFactory, dataConnectFactory) {
  override fun createConnector(firebaseApp: FirebaseApp, settings: DataConnectSettings) =
    KeywordsConnector.getInstance(firebaseApp, settings)
}
