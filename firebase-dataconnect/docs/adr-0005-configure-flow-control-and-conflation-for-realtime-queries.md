---

id: 0005
title: Configure Flow Control and Conflation for Realtime Queries
date: Tue May 19 17:28:33 EDT 2026
status: accepted
deciders: [Jetski, dconeybe]
tags: [network, grpc, coroutines, flow, backpressure, performance]
------------------------------------------------------------------

# Configure Flow Control and Conflation for Realtime Queries

## Context

We recently refactored query subscriptions to use a two-level `SharedFlow` architecture (ADR 0004) to
support declarative, self-healing connections. Initially, both the **upper connection flow**
(`connectionFlow` in `DataConnectBidiConnectStream.kt`) and the **lower query flows** (returned by
`subscribe()`) were configured with `.buffer(capacity = Channel.UNLIMITED)`.

Using `Channel.UNLIMITED` buffers introduces three severe architectural and stability risks:
1.  **Out-of-Memory (OOM) Risks**: If the backend emits updates faster than the client coroutines
can process them, the unlimited buffer will queue messages indefinitely in memory. Under sustained
high-load conditions, this leads to severe memory inflation and eventual application crashes.
2.  **No Backpressure Coordination**: Standard gRPC and HTTP/2 protocols rely on finite receive buffers
to coordinate backpressure. With `Channel.UNLIMITED`, the client never stops reading from the
socket, meaning the server is never signaled to slow down. This wastes server resources and client
network bandwidth.
3.  **UI Rendering Backlog (Staleness)**: Query subscriptions represent declarative, state-based data
(e.g. the latest document state) rather than discrete event logs. If a subscriber (such as a UI
component) is slow to process updates due to main-thread rendering overhead, an unlimited buffer
will queue historic intermediate states. When the UI catches up, it wastefully processes and renders
a long backlog of stale intermediate states sequentially, causing visible UI lag and wasted CPU
cycles.

## Decision

We will establish distinct, appropriate buffer configurations for each level of the flow:
1.  **Upper Connection Flow**: Limit the `connectionFlow` buffer to a finite capacity using
`.buffer(capacity = RECEIVE_BUFFER_SIZE)` (configured as `64`).
2.  **Lower Query Flows**: Configure the query-specific subscription flows to use conflation via
`.buffer(capacity = Channel.CONFLATED)`.

## Rationale

By applying different buffering strategies to each level, we achieve both optimal system stability
and maximum user-visible performance.

### Activating Backpressure on the Connection Flow (`RECEIVE_BUFFER_SIZE = 64`)

By changing the connection-level buffer from `UNLIMITED` to `64`, we activate native gRPC and HTTP/2
flow control.
*   If the client falls behind in processing, the connection-level buffer will fill up.
*   Once filled, the gRPC client halts reading from the transport layer.
*   This causes the TCP/HTTP/2 receive windows to shrink, sending a backpressure signal back to the
server, forcing it to slow down or buffer updates.
*   A buffer size of `64` is chosen as it is large enough to absorb transient bursts of concurrent
updates across multiplexed queries, while small enough to trigger backpressure quickly if the
client is genuinely stuck, preventing OOMs.

### Guaranteeing Data Freshness on Individual Queries (`Channel.CONFLATED`)

For query subscriptions, the user only cares about the *latest* state. Intermediate states are
transient and obsolete as soon as a newer update arrives.
*   By using `Channel.CONFLATED` (which has a capacity of 1 and overwrites on new emissions), we
guarantee that a subscriber will never process a backlog of historic, stale updates.
*   If a subscriber is busy when an update arrives, older pending updates in its queue are immediately
dropped.
*   When the subscriber finishes its current work and suspends, it instantly receives the newest update.
This saves valuable CPU cycles, prevents main-thread rendering lag, and ensures the UI is always
up-to-date.

