package com.google.android.datatransport.runtime;

/** RPC for manipulating SynchronizationGuard locking across process boundary. */
interface ITestRemoteLockRpc {

    /** Try to acuire lock or timeout. */
    boolean tryAcquireLock();

    /** Release held lock. If lock is not held - undefined behavior. */
    void releaseLock();

    /** Return PID of the process this service is running on. */
    long getPid();
}
