package com.google.firebase.firestore.remote;

import static com.google.firebase.firestore.util.Assert.hardAssertNonNull;

import com.google.firebase.firestore.core.ComponentProvider;
import com.google.firebase.firestore.util.AsyncQueue;

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
        return new GrpcCallProvider(configuration.asyncQueue, configuration.context, configuration.databaseInfo, firestoreHeaders);
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

    protected ConnectivityMonitor createConnectivityMonitor(ComponentProvider.Configuration configuration) {
        return new AndroidConnectivityMonitor(configuration.context);
    }
}
