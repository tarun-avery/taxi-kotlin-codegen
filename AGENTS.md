# Project guidance

## Goal and scope

Build a Kotlin generator that uses Taxi as the source of truth for domain POJOs,
Agrona buffer-backed flyweights, and SBE-compatible encoders and decoders.
Start with a fixed-layout Order and an Aeron IPC demonstration; follow docs/plan.md.
Do not add a trading engine, frontend, Spring service, or Aeron Cluster unless requested.

## Before changing code

- Read this file, the README, the plan, and relevant source first.
- Check the working tree and preserve existing user changes.
- Use APIs and documentation matching versions pinned in the build.
- Keep changes focused and explain decisions affecting learning goals or wire semantics.

## Correctness and architecture

- Use Taxi's compiler; do not implement a separate Taxi parser.
- Separate domain semantics, wire-layout validation, and source generation.
- Generate all implementations from the same validated representation.
- Do not use intermediate SBE XML or the official SBE compiler in the generation pipeline.
  Independent reference tooling is permitted only in compatibility tests.
- Generate deterministically: no timestamps, random identifiers, absolute local paths,
  or unstable iteration order in output. Keep generated sources under build/.
- Validate identifiers, encoding ranges, offsets, null sentinels, versions, and unsupported
  constructs. Fail with useful schema locations instead of silently guessing.
- Preserve wire compatibility: never reorder or resize existing fields silently. Keep
  reviewed layout baselines and test both directions of schema evolution.
- Share codec layout logic with flyweight adapters; avoid duplicate offset calculations.
- Treat buffers as borrowed storage. Document lifetime and thread ownership; never retain
  an Aeron callback buffer or view beyond its valid lifetime without an owned copy.
- Use DirectBuffer for reads and MutableDirectBuffer for writes. Validate message bounds
  before access, including nonzero offsets and variable-length sections.
- Keep versions pinned and use the Gradle wrapper. Select compatible dependencies before
  adding them, and avoid dynamic versions.
- Use neutral source packages and coordinates, without personal identifiers or home paths.
- Never commit credentials, local IDE state, generated/build output, or runtime data.

## Formatting

- Follow official Kotlin conventions with four-space indentation for .kt and .kts.
- Use ktlint_official, project-local .editorconfig, and pinned formatter versions.
- Run ./gradlew ktlintFormat after Kotlin edits, then ./gradlew ktlintCheck.
- Keep formatting checks connected to Gradle check/build. Avoid unrelated mass formatting.

## Verification and reporting

- Start with focused compilation and tests; compile generated output from a clean build.
- Round trips alone do not establish SBE conformance: use independently verified fixtures
  and interoperability tests for every supported wire feature.
- Cover malformed/truncated input, nonzero offsets, evolution, and deterministic output.
- Do not claim zero allocation or improved performance without measurements. Use equivalent
  work in JMH, consume results, and report JVM, hardware, warmup, forks, and allocation rates.
- Do not launch long-running benchmarks or persistent services unless requested.
- Report changed behavior, checks run, and verification gaps accurately.

## Documentation

- Keep README concise: purpose, usefulness, status, prerequisites, verified commands, and
  expected results. Distinguish implemented behavior from planned features.
- Put detailed architecture and protocol reference in docs/ and link from README.
- Update documentation when behavior changes; do not invent licenses or support channels.

## Commits

- Project-specific exception authorized by the user: implement the plan milestone
  by milestone, verify each milestone, commit it, and continue without requesting
  approval for each commit. This overrides the global review-before-commit rule
  for this project only while working through this plan.
- Keep one focused commit per milestone and report checks and limitations.
- Review the staged diff and exclude credentials, generated files, local IDE
  state, and runtime data before committing. Do not push unless requested.
