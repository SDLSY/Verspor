# AGENTS.md

Primary instructions for agentic coding in this repository.
Keep edits small, verify changes with the commands below, and do not invent features.

## Project snapshot
- Android runtime module: `:app-shell`
- Current Gradle modules: `:app-shell`, `:core-common`, `:core-model`, `:core-data`, `:core-ble`, `:core-network`, `:core-db`, `:core-ml`, `:feature-home`, `:feature-device`, `:feature-doctor`, `:feature-relax`, `:feature-trend`, `:feature-profile` (`settings.gradle.kts`)
- Legacy Android business sources are archived under `app/`; `:app-shell` is the sole Android runtime module and no longer compiles `app/` via `sourceSets`
- Build stack: AGP `8.7.3`, Kotlin `1.9.24` (`gradle/libs.versions.toml`)
- SDK levels: `compileSdk 35`, `targetSdk 35`, `minSdk 24` (`app-shell/build.gradle.kts`)
- JVM target: Java 11 / Kotlin `jvmTarget = "11"`
- Main Android libraries: Room, Retrofit/OkHttp, TFLite 2.14, MPAndroidChart, Nordic BLE, Lottie, Shimmer, Hilt
- Cloud areas: `cloud-next/` (Next.js + Supabase APIs), `ml/` (training/export/inference scripts), `contracts/` (shared schemas/DTOs)
- Gradle uses version catalog aliases (`libs.versions.toml`)

## Rule precedence and companion rule files
- This `AGENTS.md` is the authoritative agent rule file for this repo.
- Cursor rules path `.cursor/rules/`: not present.
- Cursor root file `.cursorrules`: not present.
- Copilot rules `.github/copilot-instructions.md`: not present.
- If these files are added later, merge their constraints into agent behavior instead of ignoring them.

## Working conventions for agents
- Always run commands from repo root unless a script explicitly requires another directory.
- Prefer module-qualified Gradle tasks (`:app-shell:<task>`) for clarity and stable CI logs.
- On Windows use `gradlew.bat`; on Unix/macOS use `./gradlew`.
- Make minimal, targeted diffs; preserve existing architecture and naming.
- Never commit or expose secrets/signing files.

## Build commands
```bash
# Debug APK
./gradlew :app-shell:assembleDebug

# Install debug build on connected device/emulator
./gradlew :app-shell:installDebug

# Release APK (requires keystore.properties)
./gradlew :app-shell:assembleRelease
```

Windows examples:
```powershell
gradlew.bat :app-shell:assembleDebug
gradlew.bat :app-shell:installDebug
gradlew.bat :app-shell:assembleRelease
```

Build notes:
- `:app-shell:assembleRelease` expects `keystore.properties` (template: `keystore.properties.template`).
- Release signing is conditionally applied in `app-shell/build.gradle.kts` only when file exists.
- Do not build `:app` directly as the primary runtime target; `:app-shell` is the maintained entry module.

## Lint commands
```bash
./gradlew :app-shell:lint
```

Lint notes:
- Lint is non-blocking (`abortOnError = false`).
- Disabled checks include `NullSafeMutableLiveData`, `FrequentlyChangingValue`, `RememberInComposition`.
- Treat disabled checks as tech debt, not as quality signals to ignore permanently.

## Test commands
```bash
# All local JVM unit tests
./gradlew :app-shell:testDebugUnitTest

# All connected instrumented tests (needs device/emulator)
./gradlew :app-shell:connectedDebugAndroidTest
```

Test framework versions (`gradle/libs.versions.toml`):
- Unit tests: JUnit `4.13.2`
- Instrumented: AndroidX Test JUnit `1.2.1`, Espresso `3.6.1`

Instrumentation runner:
- `androidx.test.runner.AndroidJUnitRunner` (`app-shell/build.gradle.kts`)

## Single-test cookbook (important)

### JVM unit tests (`--tests` works)
```bash
# Single class
./gradlew :app-shell:testDebugUnitTest --tests "com.example.newstart.util.PpgPeakDetectorTest"

# Single method
./gradlew :app-shell:testDebugUnitTest --tests "com.example.newstart.util.PpgPeakDetectorTest.analyze_detects_reasonable_bpm_from_synthetic_ppg"

# Pattern/wildcard
./gradlew :app-shell:testDebugUnitTest --tests "*PpgPeak*"
```

### Instrumented tests (`--tests` does NOT work)
```bash
# Single instrumented class
./gradlew :app-shell:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.newstart.ExampleInstrumentedTest

# Single instrumented method
./gradlew :app-shell:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.newstart.ExampleInstrumentedTest#useAppContext
```

Instrumentation caveats:
- Use `-Pandroid.testInstrumentationRunnerArguments.class=...` for filtering.
- Requires connected device/emulator; behaves differently from local `Test` tasks.
- Filtering/reporting is handled by AGP + adb instrumentation, not Gradle `--tests`.

## Cloud verification commands
```bash
# Start cloud-next local server
cd cloud-next && npm run dev

# Build check
cd cloud-next && npm run build

# Lint check
cd cloud-next && npm run lint
```

## Kotlin style guide (from actual code)
- Formatting follows `.editorconfig`: UTF-8, LF, 4 spaces, final newline.
- Kotlin code style is `official` (`gradle.properties`).
- Architecture pattern is MVVM + Repository + Room DAOs.
- ViewModels usually extend `AndroidViewModel` (see `ui/home/MorningReportViewModel.kt`).
- LiveData exposure pattern is standard: private mutable `_state`, public immutable `state`.
- Asynchronous UI work uses `viewModelScope.launch { ... }`.
- Data/repository mapping frequently uses extension functions at file bottom.
- Keep existing Chinese user-facing strings if editing related UI/domain logic.

## Imports and naming
- Prefer explicit imports for readability and safer refactors.
- Wildcard imports exist (for example in repository files); avoid adding new wildcard imports.
- Naming conventions:
  - Classes/types: `PascalCase`
  - Functions/properties/locals: `camelCase`
  - Constants: `UPPER_SNAKE_CASE` where appropriate, or `TAG` in companion object
  - Test names may be descriptive snake_case style (existing JUnit pattern)

## Types and state handling
- Prefer strong types/data classes over loose maps for app domain models.
- Keep nullability explicit; avoid adding `!!` in production paths.
- Use `Flow` from DAO/repository where stream semantics are already established.
- When exposing UI state, keep mutability internal to ViewModel.

## Coroutine and threading rules
- Repository network and I/O work should run in `withContext(Dispatchers.IO)`.
- ViewModel orchestration stays in `viewModelScope`.
- If catching broad exceptions in suspend code, always rethrow `CancellationException` first.
- Avoid blocking calls on main thread; preserve existing dispatcher boundaries.

## Error handling rules
- Repository APIs often return `Result<T>` for network operations; keep this contract stable.
- Build failures/messages should carry actionable text (do not swallow root cause).
- Prefer specific exception types when touching network/JSON/I/O code.
- For fallback behavior (model missing, network unavailable), keep graceful degradation behavior.

## Logging guidelines
- Android logging style: `android.util.Log` + `private const val TAG = "ClassName"` in companion object.
- Log operational events and fallback reasons; avoid logging secrets/tokens/user sensitive data.
- Python scripts currently use `print` in places; new reusable code should prefer `logging`.

## Python style guide (ml)
- Follow PEP 8 and keep scripts composable.
- Type hints are expected for public functions and important internal helpers.
- Keep standard entrypoint guard: `if __name__ == "__main__":`.
- Cloud API handlers should return structured JSON payloads (`code`, `message`, `data`/`success`).
- Avoid bare `except:`; include context when raising or returning errors.

## Security and config guardrails
- Never commit: `keystore.properties`, `local.properties`, `*.jks`, `*.keystore`, tokens, API keys.
- Endpoint config comes from Gradle properties (`DEBUG_API_BASE_URL`, `RELEASE_API_BASE_URL`).
- Do not hardcode credentials in Kotlin, Python, Gradle, or docs.
- Preserve `.gitignore` coverage for local signing and machine-specific files.

## Scope discipline
- Do not claim roadmap items are completed unless code and tests exist in this repo.
- Keep feature changes aligned with current module boundaries and architecture.
- Prefer incremental PR-sized changes over large rewrites.

<!-- gitnexus:start -->
# GitNexus — Code Intelligence

This project is indexed by GitNexus as **newstart** (16681 symbols, 30458 relationships, 300 execution flows). Use the GitNexus MCP tools to understand code, assess impact, and navigate safely.

> If any GitNexus tool warns the index is stale, run `npx gitnexus analyze` in terminal first.

## Always Do

- **MUST run impact analysis before editing any symbol.** Before modifying a function, class, or method, run `gitnexus_impact({target: "symbolName", direction: "upstream"})` and report the blast radius (direct callers, affected processes, risk level) to the user.
- **MUST run `gitnexus_detect_changes()` before committing** to verify your changes only affect expected symbols and execution flows.
- **MUST warn the user** if impact analysis returns HIGH or CRITICAL risk before proceeding with edits.
- When exploring unfamiliar code, use `gitnexus_query({query: "concept"})` to find execution flows instead of grepping. It returns process-grouped results ranked by relevance.
- When you need full context on a specific symbol — callers, callees, which execution flows it participates in — use `gitnexus_context({name: "symbolName"})`.

## When Debugging

1. `gitnexus_query({query: "<error or symptom>"})` — find execution flows related to the issue
2. `gitnexus_context({name: "<suspect function>"})` — see all callers, callees, and process participation
3. `READ gitnexus://repo/newstart/process/{processName}` — trace the full execution flow step by step
4. For regressions: `gitnexus_detect_changes({scope: "compare", base_ref: "main"})` — see what your branch changed

## When Refactoring

- **Renaming**: MUST use `gitnexus_rename({symbol_name: "old", new_name: "new", dry_run: true})` first. Review the preview — graph edits are safe, text_search edits need manual review. Then run with `dry_run: false`.
- **Extracting/Splitting**: MUST run `gitnexus_context({name: "target"})` to see all incoming/outgoing refs, then `gitnexus_impact({target: "target", direction: "upstream"})` to find all external callers before moving code.
- After any refactor: run `gitnexus_detect_changes({scope: "all"})` to verify only expected files changed.

## Never Do

- NEVER edit a function, class, or method without first running `gitnexus_impact` on it.
- NEVER ignore HIGH or CRITICAL risk warnings from impact analysis.
- NEVER rename symbols with find-and-replace — use `gitnexus_rename` which understands the call graph.
- NEVER commit changes without running `gitnexus_detect_changes()` to check affected scope.

## Tools Quick Reference

| Tool | When to use | Command |
|------|-------------|---------|
| `query` | Find code by concept | `gitnexus_query({query: "auth validation"})` |
| `context` | 360-degree view of one symbol | `gitnexus_context({name: "validateUser"})` |
| `impact` | Blast radius before editing | `gitnexus_impact({target: "X", direction: "upstream"})` |
| `detect_changes` | Pre-commit scope check | `gitnexus_detect_changes({scope: "staged"})` |
| `rename` | Safe multi-file rename | `gitnexus_rename({symbol_name: "old", new_name: "new", dry_run: true})` |
| `cypher` | Custom graph queries | `gitnexus_cypher({query: "MATCH ..."})` |

## Impact Risk Levels

| Depth | Meaning | Action |
|-------|---------|--------|
| d=1 | WILL BREAK — direct callers/importers | MUST update these |
| d=2 | LIKELY AFFECTED — indirect deps | Should test |
| d=3 | MAY NEED TESTING — transitive | Test if critical path |

## Resources

| Resource | Use for |
|----------|---------|
| `gitnexus://repo/newstart/context` | Codebase overview, check index freshness |
| `gitnexus://repo/newstart/clusters` | All functional areas |
| `gitnexus://repo/newstart/processes` | All execution flows |
| `gitnexus://repo/newstart/process/{name}` | Step-by-step execution trace |

## Self-Check Before Finishing

Before completing any code modification task, verify:
1. `gitnexus_impact` was run for all modified symbols
2. No HIGH/CRITICAL risk warnings were ignored
3. `gitnexus_detect_changes()` confirms changes match expected scope
4. All d=1 (WILL BREAK) dependents were updated

## CLI

- Re-index: `npx gitnexus analyze`
- Check freshness: `npx gitnexus status`
- Generate docs: `npx gitnexus wiki`

<!-- gitnexus:end -->
