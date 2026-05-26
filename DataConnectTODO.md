## grpc-testing library

Use features from the grpc-testing library in unit and integration tests.

* https://grpc.io/blog/graceful-cleanup-junit-tests/
* https://central.sonatype.com/artifact/io.grpc/grpc-testing
* https://github.com/grpc/grpc-java/tree/master/testing/src/main/java/io/grpc/testing

## CpuDispatcher and IODispatcher value types

The code uses the terms "blocking dispatcher" and "IO dispatcher" interchangeably.
It also uses the terms "non-blocking dispatcher" and "CPU dispatcher" interchangeably.
This is confusing and error prone.
The terms "blocking" and "non-blocking" actually refer to the _work_ that the dispatcher
is designed to perform, rather than a property of the dispatcher itself, which is confusing.

Therefore, prefer the terms "CPU" and "IO" to describe the intended work loads.
Brainstorm with Gemini to come up with good names that indicate that "CPU" and "IO"
describe the intended _work_ of the dispatcher, rather than a property of the dispatcher itself.

Add value types `CpuDispatcher` and `IODispatcher` (bikeshed on the name with gemini) to
ensure type safety and avoid swapping the two types.

Make sure the document in `CpuDispatcher` that long-running CPU operations should either
(a) occasionally call ensureActive() in suspend functions or (b) occasionally check for
thread interruption and wrap the call to a non-suspend function in runInterruptibly so that
they release the thread if cancelled eagerly. The former is preferred because it also
enables cooperative thread sharing

## Instrumentation test sharding

I can run a bunch of emulators on a remote linux box and run the integration tests in parallel.

https://medium.com/mesmerhq/shard-your-android-espresso-tests-for-faster-execution-in-parallel-e66f1b5061ae

Ask gemini to spit out the ssh and adb commands to forward the ports from the remote emulators

