---

id: 0002
title: Use replayExpirationMillis = 0 in WhileSubscribed
date: Sat May 16 22:34:19 EDT 2026
status: accepted
deciders: [Jetski, dconeybe]
tags: [network, grpc, coroutines, flow, reactive-streams]
---------------------------------------------------------

# Use replayExpirationMillis = 0 in WhileSubscribed

## Context

In ADR 0001, we rewired the bidirectional gRPC connection to utilize `SharingStarted.WhileSubscribed()` in the `sharedFlow` inside `DataConnectBidiConnectStream.kt`.
This was done to ensure that the gRPC connection automatically opens when there is at least one active query subscriber, and automatically closes when the last query subscriber unsubscribes.

To distribute incoming events to new subscribers, the flow is shared with `replay = 1`.
However, when all subscribers unsubscribe, the subscriber count drops to `0`, and the `WhileSubscribed` policy halts the upstream flow, closing the connection.

Because the default configuration of `SharingStarted.WhileSubscribed()` keeps the replay cache forever (`replayExpirationMillis = Long.MAX_VALUE`), the `sharedFlow` continues to hold the last emitted event in its replay cache. This event (whether `Event.Started`, `Event.Message`, or `Event.Completed`) contains stale references and state from the **now-closed connection**.

When a new query subscriber subsequently subscribes to the stream:
1. The subscriber count increases to `1`.
2. The subscriber immediately receives the stale event (`Event.Started`, `Event.Message`, or `Event.Completed`) from the replay cache.
3. Simultaneously, `WhileSubscribed` restarts the upstream flow, initiating a new connection and starting to emit fresh events.
4. The subscriber processes the stale event, leading to incorrect behavior depending on the event type:
- If it is `Event.Started` or `Event.Message`, the subscriber calls `Subscriber.setOutgoingRequests` with the **dead connection channel**. When the restarted connection subsequently emits a new `Event.Started`, `Subscriber.setOutgoingRequests` detects that a channel is already set and throws an `IllegalStateException` due to the mismatch.
- If it is `Event.Completed`, the subscriber immediately terminates its own flow collection without waiting for the restarted connection to establish.
5. In the case of an `IllegalStateException`, the exception cancels the subscriber flow, which propagates upstream and immediately shuts down the newly established connection with a `ChildCancelledException`.

## Decision

We will configure the sharing policy of the internal `sharedFlow` in `DataConnectBidiConnectStream.kt` to use `SharingStarted.WhileSubscribed(replayExpirationMillis = 0)`.

## Rationale

Setting `replayExpirationMillis = 0` guarantees that:
- The instant the active subscriber count drops to `0` (meaning all queries have unsubscribed and the underlying connection is closed), the `sharedFlow` **immediately clears its replay cache**.
- When a new query subscriber starts collecting the flow later, the replay cache is empty, so the subscriber receives no stale connection events.
- The new subscriber safely waits for the new connection to establish, receiving only fresh lifecycle and data events from the active connection, allowing seamless reconnections.

## Options Considered

* **Option A: Custom Subscriber-Count Tracking in Manager State**
  * *Pros:* Avoids depending on the inner workings of the coroutine flow replay cache configuration.
  * *Cons:* Extremely complex. Requires manually incrementing/decrementing subscription counts in `DataConnectBidiConnectStream.subscribe()`, exposing it via `StateFlow`, and launching a background watcher in `RealtimeQueryManager` to transition the manager back to `State.Disconnected` on `0` subscribers. This completely bypasses/defeats the benefits of the `WhileSubscribed()` sharing policy.
  * *Reason for Rejection:* It is overly complex, brittle, prone to race conditions, and defeats the purpose of the declarative stream state machine enabled by `WhileSubscribed()`.
* **Option B: Rebuilding the Stream on Every Reconnection (Without Cache Clearance)**
  * *Pros:* None.
  * *Cons:* Recreating the stream objects on the client requires heavy coordination between the manager and stream instances.
  * *Reason for Rejection:* Simple cache expiration is much more elegant.

## Consequences

* **Positive:**
  * Extremely localized, single-line change that elegantly solves a complex lifecycle bug.
  * Preserves all benefits of declarative, self-cleaning connection sharing provided by `WhileSubscribed()`.
  * Reconnections are completely transparent and robust.
* **Negative/Risks:**
  * If a subscriber disconnects and immediately reconnects within less than a millisecond, they might not get the replay cache, but since they want a fresh reconnection anyway when the stream is closed, this is the desirable behavior.

