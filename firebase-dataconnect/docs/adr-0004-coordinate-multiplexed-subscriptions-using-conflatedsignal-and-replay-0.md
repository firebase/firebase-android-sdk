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

The Data Connect SDK manages a multiplexed bidirectional gRPC connection to support realtime query
subscriptions. Historically, query multiplexing was achieved using a single, connection-level
shared flow (the **"upper" shared flow**), while the tracking of active query subscribers, their
individual states, and the orchestration of `subscribe`, `resume`, and `cancel` requests was
managed manually by a complex utility called `SubscriptionStateManager`.

To support late subscribers joining active queries, the connection flow used `replay = 1`. This,
however, introduced a critical issue where new subscribers immediately received stale events from
the replay cache. We attempted to mitigate this with `replayExpirationMillis = 0` (ADR 0002) and
client-side sequence number filtering (ADR 0003).

Despite these workarounds, the architecture remained fragile. It suffered from a latent
**in-flight request race condition** (documented in ADR 0003) where a late subscriber could consume
an in-flight response intended for a different subscriber, because sequence numbers could not
physically differentiate responses. Furthermore, `SubscriptionStateManager` was highly complex,
hard to maintain, and had extremely subtle, error-prone multithreading requirements.

## Decision

We will completely redesign the multiplexing and connection lifecycle architecture to utilize **two
levels of `SharedFlow`** coordinated by a lock-free connection state machine and a new
`ConflatedSignal` utility.

Specifically, we will:
1.  **Establish Two Levels of SharedFlow**:
*   **Upper Shared Flow (`connectionFlow`)**: Maintains the active bidirectional gRPC connection
with the server. Shared using `replay = 0` and `SharingStarted.WhileSubscribed(0)`.
*   **Lower Shared Flows**: Spawned per unique query (cached in `RealtimeQueryManager`'s
`flowByQueryId`). Each lower flow is shared using `replay = 0` and `WhileSubscribed(0)`.
It multiplexes all active local subscribers for queries sharing the exact same operation
name and variables.
2.  **Retire `SubscriptionStateManager` and Sequence Numbers**: Completely delete the fragile,
manual subscriber-tracking class and remove all sequence number filtering logic.
3.  **Coordinate via `ConflatedSignal`**: Introduce a thread-safe, lock-free coroutine utility
`ConflatedSignal` to coordinate `subscribe` and `resume` requests.
4.  **Implement `SubscriptionState` Machine**: Manage query-specific connection states via an
`AtomicReference<SubscriptionState>` transitioning through:
*   `Disconnected`: No active connection and no pending subscriptions.
*   `DisconnectedWithPendingSubscription`: Disconnected, but has an active subscriber waiting
for reconnection.
*   `Connected`: Connected, holding the channel, a `ConflatedSignal`, and running a lazy
`subscribeOrResumeLoop` coroutine.

## Rationale

The entire two-level `SharedFlow` design was motivated by the desire to leverage **standard,
declarative coroutines library operators** that perfectly express our desired semantics, rather
than building and maintaining complex custom synchronization logic.

### Leveraging Built-In Coroutine Operators

* **`WhileSubscribed(replayExpirationMillis = 0)`**: By using this policy at both the upper and
  lower flow levels, we let the coroutines library handle the subscription reference-counting
  natively. The connection automatically opens when the first query is subscribed to, and closes
  when the last subscriber leaves. Similarly, the query-level resources automatically clean up
  when a query has zero active collectors.
* **`retryWhen`**: Relying on standard flow sharing allows us to use `retryWhen` on the upper flow.
  This provides a clean, robust, and standardized hook to implement upcoming reliability
  features, such as exponential backoff and instant reconnection on system network state change
  events.

### Bypassing Stale Cache and Race Conditions by Design (`replay = 0`)

By setting `replay = 0` at both levels, we eliminate the replay cache entirely. New query
subscribers no longer receive stale replayed messages. Because they wait for fresh emissions
originating from the newly coordinated `subscribe` or `resume` requests, the latent "in-flight"
race condition is naturally avoided for subsequent subscribers.

### Rationale for `resume` on Late Subscribers

When a new subscriber joins an already active query flow, the lower flow's `onSubscription` block
triggers and signals the connection's `ConflatedSignal`. Because we are already subscribed to the
query, the `subscribeOrResumeLoop` sends a `resume` request to the server.

This `resume` request kicks the server to immediately re-run the query and send an updated result
down the stream. This is highly valuable because:
1.  **Data Freshness**: Some queries are not "fully" real-time and may not emit updates frequently. A
`resume` ensures the late subscriber does not receive stale data that has been residing on the
client.
2.  **User Intent Proxy**: A new local subscriber (e.g., a user navigating to a new screen) is a
strong proxy for user intent indicating they want the absolute latest results.

## Options Considered

* **Option A: The Old Code (Single Shared Flow + Manual `SubscriptionStateManager`)**
  * *Pros:* Avoids the minor overhead of creating multiple `SharedFlow` and `Job` instances
    per query.
  * *Cons:* Extremely complex, fragile, and required custom multithreading locks. The team was
    effectively rebuilding `WhileSubscribed` logic manually, which is highly error-prone.
  * *Reason for Rejection:* The manual state tracking was too fragile. The benefits of delegating
    concurrency and lifecycle to standard, battle-tested library operators far outweighed the
    minimal overhead of secondary flows.
* **Option B: Rebuilding Connection Flow on every subscription**
  * *Pros:* Simple 1-to-1 connection-to-subscriber mapping.
  * *Cons:* Severely wastes server and client resources by opening multiple distinct gRPC
    connections for identical or overlapping queries.
  * *Reason for Rejection:* Multiplexing over a single connection is a hard performance
    requirement of the SDK.

## Consequences

* **Positive:**
  * Massive complexity reduction by replacing fragile custom state with standard operators
    (`WhileSubscribed` and `retryWhen`).
  * Complete eradication of stale replay cache bugs.
  * Seamless, native support for automatic reconnection and subscription resumption.
  * Clean foundation for implementing robust exponential backoff in the future.
* **Negative/Risks / Tradeoffs:**
  * Requires developers to have a highly sophisticated and intricate understanding of
    `SharedFlow` internals (such as subscription counters, start policies, and cache expiration).
    However, this is a necessary tradeoff, as implementing these concurrency semantics manually
    would require the same deep knowledge and result in far more fragile code.
  * The **"in-flight request" race condition** (where a late subscriber can consume a response
    intended for a different subscriber) remains unresolved. Because we removed sequence
    numbers and have no alternative correlation mechanism, this remains a known limitation
    that will likely require server cooperation (e.g., correlation tokens) to fully fix.

