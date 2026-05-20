# Architecture Decision Records (ADRs)

This directory serves as a historical record of the design and architectural decisions made for this project.
These documents are designed to capture the context, rationale, and consequences of key decisions,
helping both human developers and future AI coding agents understand the system's design and ground their decisions.

---

## How to Use this Directory

### For Reading (Grounding Decisions)

Before modifying any significant component, starting a new feature, or refactoring code:
1.  Review the **Decision Registry** below.
2.  Read any ADRs tagged with components or concepts relevant to your task.
3.  Ensure your proposed changes do not violate the decisions accepted in these records.

### For Writing (Recording Decisions)

When you need to make a significant architectural choice (e.g., choosing a library, changing data flow, defining a new component boundary):
1.  Create a new file in this directory named `adr-NNNN-short-description.md`, starting from `adr-0001.md`.
2.  Use the template in [adr-template.md](adr-template.md).
3.  Fill out the sections honestly and concisely. Prioritize explaining the **WHY** behind the
decisions, the benefits, and why the choices are valid and worthwhile. **AVOID** merely repeating
the technical choices without any justifications (developers can just read the git commit
history for what changed; ADRs exist to capture the reasoning). If the rationale, trade-offs,
or benefits are not fully known or clear, **liberally ask the user for clarification** before
writing. It is of paramount importance that these details are recorded accurately and are
absolutely correct.
4.  Update the **Decision Registry** in this file (`docs/README.md`) to list your new ADR.

---

## Guidelines for AI Coding Agents (CRITICAL)

### 1. Determine the Current Date Accurately

> [!IMPORTANT]
> When creating or updating an ADR, **do NOT guess or hallucinate the current date and time.**
> You MUST run the system command `date` (or use an equivalent MCP tool) to determine the exact current date before writing it to the metadata block. Incorrect dates cause severe timeline confusion.

### 2. Formatting and Definition of Done

> [!IMPORTANT]
> As the **very last step** before completing any task that modifies or creates Markdown (`.md`) files in this repository (including this `docs` directory), you **MUST** run the formatting script:
>
> ```bash
> ./scripts/spotlessApply.zsh
> ```
>
> (Or `../scripts/spotlessApply.zsh` if running from within the `docs/` directory).
> A task is NOT considered complete until the files have been successfully formatted by this script.

### 3. Focus on the "Why" (Rationale and Benefits)

> [!IMPORTANT]
> When writing ADRs, prioritize explaining the **rationale, trade-offs, and benefits** of the
> decisions. Do not just repeat the technical implementation details—explain **WHY** they were
> chosen and why the solutions are valid. ADRs are design documents meant to convey historical
> context and reasoning, not code diff summaries (which can be read in the git commit history).
>
> **CRITICAL**: If the rationale, trade-offs, or benefits of a decision are not fully known or are
> unclear to you, **you MUST liberally ask the user for clarification** before writing the document.
> It is of paramount importance that these architectural details are recorded accurately and are
> absolutely correct. Do not make assumptions or guess.

---

## Decision Registry

| ID                                                                                          | Title                                                                   | Date                         | Status     | Tags                                                       |
|:--------------------------------------------------------------------------------------------|:------------------------------------------------------------------------|:-----------------------------|:-----------|:-----------------------------------------------------------|
| [0001](adr-0001-rewire-bidi-connection-to-grpcbidiflow.md)                                  | Rewire Bidirectional gRPC Connection to GrpcBidiFlow                    | Fri May 15 18:33:07 EDT 2026 | accepted   | network, grpc, coroutines, reactive-streams                |
| [0002](adr-0002-use-replay-expiration-millis-0-in-while-subscribed.md)                      | Use replayExpirationMillis = 0 in WhileSubscribed                       | Sat May 16 22:34:19 EDT 2026 | superseded | network, grpc, coroutines, flow, reactive-streams          |
| [0003](adr-0003-sequence-number-filtering-for-stale-replayed-data.md)                       | Sequence Number Filtering for Stale Replayed Data                       | Sun May 17 00:02:50 EDT 2026 | superseded | network, grpc, coroutines, flow, multiplexing              |
| [0004](adr-0004-coordinate-multiplexed-subscriptions-using-conflatedsignal-and-replay-0.md) | Coordinate Multiplexed Subscriptions using ConflatedSignal and Replay=0 | Tue May 19 15:58:34 EDT 2026 | accepted   | network, grpc, coroutines, flow, multiplexing              |
| [0005](adr-0005-configure-flow-control-and-conflation-for-realtime-queries.md)              | Configure Flow Control and Conflation for Realtime Queries              | Tue May 19 17:28:33 EDT 2026 | accepted   | network, grpc, coroutines, flow, backpressure, performance |

*(Add new records to this table in chronological order. Refer to the status lifecycle: `proposed` -> `accepted` -> `superseded`/`deprecated`)*
