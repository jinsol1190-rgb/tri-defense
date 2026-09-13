# AGENTS.md

# Tri-Defense Development Guide

This repository implements the Tri-Defense MVP.

This document defines repository-wide development rules for all AI agents and developers.

---

# 1. Project Goal

The objective is to implement a working MVP for the blockchain hackathon.

This is NOT a production service.

The objective is an end-to-end demonstration of the following scenario.

User A

↓

AI detects DeepVoice attack

↓

zkML proof generated

↓

Threat registered on blockchain

↓

User B automatically receives protection.

---

# 2. Source of Truth

Always follow documents in this order.

1. Implementation Specification (Codex)
2. Proposal Document
3. README

If documents conflict,

Implementation Specification overrides Proposal.

Never modify the Proposal document.

---

# 3. Development Philosophy

Implement before optimizing.

Do not invent features.

Do not redesign architecture.

Follow the existing proposal.

Preserve module boundaries.

---

# 4. Repository Responsibilities

android/

Android application.

Contains

- UI
- Audio Input
- Room
- Call Screening
- Local Cache

Never implement blockchain logic here.

---

backend/

Responsible only for

- REST API
- Submission
- Synchronization
- Registry queries

Never perform AI inference.

---

contracts/

Contains

ThreatRegistry

Verifier Adapter

Deployment Scripts

No Android code.

---

ai/

Contains

- preprocessing
- lightweight model
- precise model
- voiceprint
- evaluation

No blockchain logic.

---

dashboard/

Visualization only.

Never become business logic.

---

shared/

Shared DTOs

Schemas

Constants

API Contracts

Used by every module.

---

docs/

Architecture

Decisions

API

Limitations

Test Report

---

# 5. Development Rules

Every Pull Request must

- compile
- pass tests
- update README when needed
- update docs when architecture changes

Never leave broken builds.

---

# 6. Mock Policy

Every feature must explicitly state one of

REAL

MOCK

SAMPLE

Never present MOCK as REAL.

---

# 7. AI Rules

Never fabricate AI outputs.

Never hardcode successful inference.

Never fake VoiceprintHash.

Never fake Proof.

Placeholder models are allowed during P0.

---

# 8. Blockchain Rules

Never bypass proof verification.

Never skip Verifier.

Never directly trust client payload.

ThreatRegistry is the only source of registered threats.

Room is only a cache.

---

# 9. Android Rules

Do not assume

- real call recording
- unrestricted audio capture
- hidden Android APIs

Always verify permissions.

If unavailable,

record limitation in docs.

---

# 10. Documentation

Every implemented feature must include

implementation

limitations

remaining work

README must always reflect current state.

---

# 11. Coding Style

Prefer readability.

Avoid unnecessary abstraction.

Small reusable functions.

Meaningful names.

Avoid magic numbers.

---

# 12. Naming

Use existing proposal terminology.

Examples

VoiceprintHash

ThreatRegistry

RiskScore

Proof

Verifier

Room

Do not rename concepts.

---

# 13. Git Rules

One feature per branch.

One responsibility per PR.

Keep commits focused.

---

# 14. Development Priority

Always implement in order.

P0

Repository
Environment
Android PoC
AI PoC
zkML PoC
Registry

↓

P1

Integration

↓

P2

Optimization

Do not skip priorities.

---

# 15. Forbidden

Do not

- invent UI
- invent blockchain features
- invent APIs
- remove proposal functionality
- silently replace architecture
- claim unsupported features

---

# 16. Required Output

Every completed task must report

What changed

How to run

How to test

Current limitations

Next suggested task

---

# 17. When Unsure

If requirements are unclear

STOP

Explain ambiguity

Propose options

Wait for decision

Never guess.

---

# 18. Definition of Done

A task is complete only if

Code builds

Tests pass

README updated

Docs updated

No broken dependency

No TODO preventing execution

Otherwise

status = IN PROGRESS
