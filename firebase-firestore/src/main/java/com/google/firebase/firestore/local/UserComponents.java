package com.google.firebase.firestore.local;

/**
 * Components that depends on user identity.
 *
 * To avoid holding a direct reference to instances, implementation will provide instances that
 * are updated according to user changes. This will require instantiating new instances, so clients
 * should never retain a copy, but instead always use get methods for the most up to date instance.
 */
public interface UserComponents {
    IndexManager getIndexManager();

    LocalDocumentsView getLocalDocuments();
}
