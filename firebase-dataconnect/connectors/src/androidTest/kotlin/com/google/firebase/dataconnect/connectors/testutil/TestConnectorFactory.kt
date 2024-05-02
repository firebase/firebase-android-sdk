package com.google.firebase.dataconnect.connectors.testutil

import com.google.firebase.FirebaseApp
import com.google.firebase.dataconnect.*
import com.google.firebase.dataconnect.generated.*
import com.google.firebase.dataconnect.testutil.FactoryTestRule
import com.google.firebase.dataconnect.testutil.TestDataConnectFactory
import com.google.firebase.dataconnect.testutil.TestFirebaseAppFactory

/**
 * A JUnit test rule that creates instances of a connector for use during testing, and closes their
 * underlying [FirebaseDataConnect] instances upon test completion.
 */
abstract class TestConnectorFactory<T : GeneratedConnector>(
  private val firebaseAppFactory: TestFirebaseAppFactory,
  private val dataConnectFactory: TestDataConnectFactory
) : FactoryTestRule<T, Nothing>() {

  abstract fun createConnector(firebaseApp: FirebaseApp, settings: DataConnectSettings): T

  override fun createInstance(params: Nothing?): T {
    val firebaseApp = firebaseAppFactory.newInstance()

    val connector = createConnector(firebaseApp, dataConnectFactory.backend.dataConnectSettings)

    // Get the instance of `FirebaseDataConnect` from the `TestDataConnectFactory` so that it will
    // register the instance and set any settings required for talking to the backend.
    val dataConnect = dataConnectFactory.newInstance(firebaseApp, connector.dataConnect.config)

    check(dataConnect === connector.dataConnect) {
      "DemoConnector.getInstance() returned an instance " +
        "associated with FirebaseDataConnect instance ${connector.dataConnect}, " +
        "but expected it to be associated with instance $dataConnect"
    }

    return connector
  }

  override fun destroyInstance(instance: T) {
    // Do nothing in `destroyInstance()` since `TestDataConnectFactory` will do all the work of
    // closing the `FirebaseDataConnect` instance.
  }
}
