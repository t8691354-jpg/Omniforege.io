package com.example.data.ai

object PromptEnhancer {

    fun enhanceUserPrompt(rawIdea: String, targetPlatform: String = "Android (Kotlin/Compose)"): String {
        val trimmed = rawIdea.trim()
        if (trimmed.isBlank()) return ""

        return """
# ENTERPRISE PRODUCT REQUIREMENTS SPECIFICATION (PRD ×200)

## 1. Executive Summary & Vision
Autonomous software blueprint generated for: **${trimmed}**
- **Target Platform:** $targetPlatform
- **Design System:** Material Design 3 (Dynamic Color, Edge-to-Edge Insets, 8dp Grid)
- **Architecture:** Unidirectional Data Flow (UDF) + MVVM + Clean Architecture Domain Separation

## 2. Critical User Journeys (CUJ)
1. **Zero-Friction Launch:** Instant onboarding with local state bootstrap and real-time offline cache.
2. **Core Operation Flow:** Low-latency responsive interactions with optimistic UI updates and instant feedback ripples.
3. **Multi-Agent Orchestration:** Automatic task breakdown, security audits, and continuous build integration.
4. **Data Durability:** Atomic state persistence with rollback capabilities and SHA-256 hashed checkpoints.

## 3. High-Level Technical Specifications
- **Presentation Layer:** Jetpack Compose with reactive StateFlows and Window Size Class support.
- **Business Logic Layer:** Kotlin Coroutines and Flows with bounded exception recovery.
- **Persistence & Cloud:** Zero-trust Firestore security model with Google Sign-In gatekeeper.
- **Telemetry & Resilience:** In-memory Circuit Breaker, exponential backoff with jitter, token budget enforcement.

## 4. Autonomous Engineering Directives
- **Chief AI Architect:** Formulate database schemas, API contracts, and dependency graphs.
- **Full-Stack Engineer:** Generate idiomatic, modularized, null-safe Kotlin/TypeScript implementations.
- **Security Auditor:** Enforce OWASP Top 10 compliance, input validation, and secret sanitization.
- **DevOps Specialist:** Validate packaging, lint hygiene, and artifact distribution pipelines.
        """.trimIndent()
    }
}
