// Copyright 2024 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
//
// You may obtain a copy of the License at
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.firebase.firestore.remote;

import static com.google.firebase.firestore.util.Assert.hardAssertNonNull;

import com.google.firebase.firestore.core.ComponentProvider;

/**
 * Initializes and wires up remote components for Firestore.
 *
 * <p>Implementations provide custom components by overriding the `createX()` methods.
 * <p>The RemoteComponentProvider is located in the same package as the components in order to have
 * package-private access to the components.
 */
public class RemoteComponenetProvider {

  private GrpcCallProvider grpcCallProvider;
  private RemoteSerializer remoteSerializer;
  private FirestoreChannel firestoreChannel;
  private Datastore datastore;
  private ConnectivityMonitor connectivityMonitor;

  public void initialize(ComponentProvider.Configuration configuration) {
    remoteSerializer = createRemoteSerializer(configuration);
    grpcCallProvider = createGrpcCallProvider(configuration);
    firestoreChannel = createFirestoreChannel(configuration);
    datastore = createDatastore(configuration);
    connectivityMonitor = createConnectivityMonitor(configuration);
  }

  public GrpcCallProvider getGrpcCallProvider() {
    return hardAssertNonNull(grpcCallProvider, "grpcCallProvider not initialized yet");
  }

  public RemoteSerializer getRemoteSerializer() {
    return hardAssertNonNull(remoteSerializer, "remoteSerializer not initialized yet");
  }

  public FirestoreChannel getFirestoreChannel() {
    return hardAssertNonNull(firestoreChannel, "firestoreChannel not initialized yet");
  }

  public Datastore getDatastore() {
    return hardAssertNonNull(datastore, "datastore not initialized yet");
  }

  public ConnectivityMonitor getConnectivityMonitor() {
    return hardAssertNonNull(connectivityMonitor, "connectivityMonitor not initialized yet");
  }

  protected GrpcCallProvider createGrpcCallProvider(ComponentProvider.Configuration configuration) {
    FirestoreCallCredentials firestoreHeaders =
        new FirestoreCallCredentials(configuration.authProvider, configuration.appCheckProvider);
    return new GrpcCallProvider(
        configuration.asyncQueue,
        configuration.context,
        configuration.databaseInfo,
        firestoreHeaders);
  }

  protected RemoteSerializer createRemoteSerializer(ComponentProvider.Configuration configuration) {
    return new RemoteSerializer(configuration.databaseInfo.getDatabaseId());
  }

  protected FirestoreChannel createFirestoreChannel(ComponentProvider.Configuration configuration) {
    return new FirestoreChannel(
        configuration.asyncQueue,
        configuration.authProvider,
        configuration.appCheckProvider,
        configuration.databaseInfo.getDatabaseId(),
        configuration.metadataProvider,
        getGrpcCallProvider());
  }

  protected Datastore createDatastore(ComponentProvider.Configuration configuration) {
    return new Datastore(configuration.asyncQueue, getRemoteSerializer(), getFirestoreChannel());
  }

  protected ConnectivityMonitor createConnectivityMonitor(
      ComponentProvider.Configuration configuration) {
    return new AndroidConnectivityMonitor(configuration.context);
  }
}
