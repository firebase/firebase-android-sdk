---

id: 0004
title: Coordinate Multiplexed Subscriptions using ConflatedSignal and Replay=0
date: Tue May 19 15:58:34 EDT 2026
status: accepted
deciders: [Jetski, dconeybe]
tags: [network, grpc, coroutines, flow, multiplexing]
supersedes: [0002, 0003]
------------------------

# Coordinate Multiplexed Subscriptions using ConflatedSignal and Replay=0

## Context

In previous designs (documented in ADR 0002 and ADR 0003), we shared the multiplexed bidirectional
gRPC connection flow using `replay = 1` to allow late subscribers to join an active connection.
This introduced a critical issue where new subscribers immediately received stale connection
lifecycle and data events from the replay cache.

We attempted to solve this by setting `replayExpirationMillis = 0` to clear the cache when
subscribers dropped to zero (ADR 0002), and by introducing a client-side sequence number filter
to discard stale replayed data when new subscribers joined while the stream was already active
(ADR 0003).

However, there was a severe, latent **in-flight request race condition** (documented in ADR 0003)
that the sequence number approach could not address: if a new subscriber joined exactly between
another subscriber's `subscribe` request and its corresponding response from the server, the new
subscriber would incorrectly consume that in-flight response, as its sequence number was valid.

Additionally, the state management implemented by `SubscriptionStateManager` and
`SubscriptionStateManager.Subscriber` was highly complex, fragile, and difficult to serialize
safely under concurrent access.

## Decision

We will:
1.  **Disable Replay Cache**: Share the connection flow in `DataConnectBidiConnectStream.kt` with
`replay = 0`, completely eliminating the replay cache.
2.  **Remove Sequence Numbers**: Completely remove the sequence number tracking and filtering
from the codebase since there is no longer a replay cache to filter.
3.  **Retire SubscriptionStateManager**: Completely delete the `SubscriptionStateManager` and its
nested `Subscriber` class.
4.  **Introduce `ConflatedSignal`**: Introduce a new internal coroutine utility, `ConflatedSignal`,
to coordinate subscription events.
5.  **Implement `SubscriptionState` Machine**: Manage connection states via a robust, lock-free
`AtomicReference<SubscriptionState>` state machine with three states:
*   `Disconnected`: No active connection and no pending subscriptions.
*   `DisconnectedWithPendingSubscription`: Disconnected, but has a subscription that needs to
be sent once reconnected.
*   `Connected`: Active stream connection, holding reference to the outgoing channel, a
`ConflatedSignal` for request coordination, and a lazy `subscribeOrResumeLoop` coroutine job.

## Rationale

* **Eliminating Stale Data by Design**: By setting `replay = 0`, we resolve all stale-cache
  bugs fundamentally. Late subscribers can no longer receive stale events because the stream
  maintains no replay history.
* **Simplified Architecture**: Removing `SubscriptionStateManager` and sequence number
  filtering drastically reduces code complexity, making the codebase much easier to maintain
  and debug.
* **Race-Resistant Coordination**: Instead of relying on the flow's replay cache to distribute
  the `outgoingRequests` channel to subscribers (so they can send their own requests), we
  inverted the responsibility:
  * Subscribers simply signal the connection's `ConflatedSignal` that they want to subscribe
    or resume.
  * The `Connected` state runs a dedicated, serialized `subscribeOrResumeLoop` that collects
    these signals and safely sends the appropriate `subscribe` or `resume` requests over the
    channel.
  * This ensures that all request transmissions are naturally coordinated.
* **Resilient Reconnection**: The `SubscriptionState` explicitly tracks if a subscription was
  pending when a disconnect occurred (`DisconnectedWithPendingSubscription`). Upon reconnection,
  it immediately triggers `subscribeOrResumeSignal.signal()`, seamlessly resuming the
  subscription.

## Options Considered

* **Option A: Maintain `replay = 1` and implement complex request-response correlation**
  * *Pros:* Keeps the existing flow distribution structure.
  * *Cons:* Requires server-side protocol changes to echo back correlation tokens in
    `StreamResponse` to solve the in-flight race condition. This is highly impractical for
    a pure client-side SDK update.
  * *Reason for Rejection:* It is not feasible to require server-side protocol changes, and
    any client-side attempt to serialize requests without changing `replay = 1` was
    excessively complex.
* **Option B: Rebuild connection flow on every subscription**
  * *Pros:* Simple 1-to-1 connection-to-subscriber mapping.
  * *Cons:* Bypasses multiplexing entirely. We would open multiple costly gRPC connections
    for identical queries, wasting client and server resources.
  * *Reason for Rejection:* Multiplexing queries over a single connection is a core
    performance requirement of the Data Connect SDK.

## Consequences

* **Positive:**
  * Complete elimination of stale replay cache bugs.
  * Significant code reduction (deleted `SubscriptionStateManager` and sequence filtering).
  * Native, elegant support for automatic reconnection and subscription resumption.
* **Negative/Risks / Known Limitations:**
  * Requires maintaining a new concurrency utility (`ConflatedSignal`), though this class is
    lightweight, heavily unit-tested, and stable.
  * The **"in-flight request" race condition** (where a late subscriber can consume a response
    intended for a different subscriber) remains unresolved. Because we removed sequence
    numbers and have no alternative correlation mechanism, this remains a known limitation
    that will likely require server cooperation (e.g., correlation tokens) to fully fix.

