package com.google.firebase.storage.ktx

/**
 * Used to emit events about the progress of storage tasks.
 */
abstract class StorageProgress<T> private constructor() {
    /**
     * Called periodically as data is transferred and can be used to populate an upload/download indicator.
     */
    class InProgress<T>(val snapshot: T) : StorageProgress<T>()

    /**
     * Called any time the upload/download is paused.
     */
    class Paused<T>(val snapshot: T) : StorageProgress<T>()
}
