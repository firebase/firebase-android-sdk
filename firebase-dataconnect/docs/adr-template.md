---

id: NNNN # Replace with next sequential number, e.g., 0001
title: Title of the Decision
date: Fri 01 Jan 1980 12:34:56 EDT (example) # REQUIRED: Run 'date' command to get this. DO NOT GUESS.
status: proposed # proposed | accepted | superseded | deprecated
deciders: [Author/Agent Name, User Name]
tags: [tag1, tag2] # e.g., database, network, serialization
supersedes: # ID of superseded ADR, if applicable (e.g., 0002)
--------------------------------------------------------------

# [Title of the Decision]

## Context

*What is the context of this decision? Describe the current situation, the problem you are trying to solve, and any constraints (technical, business, timeline) that you had to work within.*

## Decision

*State the decision clearly. This should be a concise, active-voice statement of what we are doing.*

*Example: "We will use Protocol Buffers (protobuf) for serializing state update messages over WebSocket."*

## Rationale

*Why did we make this choice? Explain the reasoning behind the decision. How does it solve the problem described in Context? What are the key benefits?*

## Options Considered

*Briefly describe other options that were investigated but rejected, and why they were rejected. This is critical to prevent future agents from proposing them again.*

* **Option A: [Name]**
  * *Pros:* ...
  * *Cons:* ...
  * *Reason for Rejection:* ...
* **Option B: [Name]**
  * *Pros:* ...
  * *Cons:* ...
  * *Reason for Rejection:* ...

## Consequences

*What are the trade-offs? What are the good and bad things that happen as a result of this decision?*

* **Positive:** (e.g., "Improved read performance", "Better type safety")
* **Negative/Risks:** (e.g., "Increased binary size", "Requires learning a new DSL")

