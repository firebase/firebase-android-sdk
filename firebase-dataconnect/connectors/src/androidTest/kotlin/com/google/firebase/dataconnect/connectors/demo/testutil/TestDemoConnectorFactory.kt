package com.google.firebase.dataconnect.connectors.demo.testutil

import com.google.firebase.dataconnect.connectors.demo.DemoConnector
import com.google.firebase.dataconnect.connectors.demo.getInstance
import com.google.firebase.dataconnect.testutil.FactoryTestRule
import com.google.firebase.dataconnect.testutil.TestDataConnectFactory
import com.google.firebase.dataconnect.testutil.TestFirebaseAppFactory

/**
 * A JUnit test rule that creates instances of [DemoConnector] for use during testing, and closes
 * their underlying [FirebaseDataConnect] instances upon test completion.
 */
class TestDemoConnectorFactory(
  val firebaseAppFactory: TestFirebaseAppFactory,
  val dataConnectFactory: TestDataConnectFactory
) : FactoryTestRule<DemoConnector, Nothing>() {

  override fun createInstance(params: Nothing?): DemoConnector {
    val firebaseApp = firebaseAppFactory.newInstance()

    val connector = DemoConnector.getInstance(firebaseApp)

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

  override fun destroyInstance(instance: DemoConnector) {
    // Do nothing in `destroyInstance()` since `TestDataConnectFactory` will do all the work of
    // closing the `FirebaseDataConnect` instance.
  }
}
