# Contributing to RuntimeLens

Thank you for helping make Java runtime behavior easier to understand before production.

RuntimeLens is intentionally built around small, explainable rules. Contributions are most valuable when they identify a runtime-risk pattern with enough evidence that a developer can understand both the warning and the recommended direction.

## Before opening a pull request

For normal bugs and features, open or search a GitHub Issue first when discussion would help.

For suspected security vulnerabilities, **do not open a public issue**. Follow [`SECURITY.md`](SECURITY.md).

## Development requirements

- Java 17+
- Git
- Bash on macOS/Linux, or PowerShell on Windows

Build and run tests:

```bash
./build.sh
```

Windows:

```powershell
.\build.ps1
```

Run the demo scan:

```bash
java -jar dist/runtimelens.jar scan examples/demo
```

## Adding a rule

RuntimeLens rules live under:

```text
src/main/java/io/github/shashank022/runtimelens/rules/
```

A rule should provide:

- a stable rule ID such as `RL1011`;
- a default severity;
- a clear category;
- a short title;
- an explanation of why the pattern matters at runtime;
- a recommendation that preserves developer judgment;
- a focused detector;
- tests showing positive and negative cases.

### Rule-quality checklist

Before proposing a new rule, ask:

1. Does the code compile successfully despite the risk?
2. Is the runtime consequence meaningful enough to warn about?
3. Can the pattern be detected with reasonable confidence?
4. Can we show the exact source evidence that triggered it?
5. Is the recommendation safer than blindly rewriting the code?
6. Have obvious false-positive cases been tested?

RuntimeLens prefers **fewer high-value findings** over hundreds of noisy style warnings.

## Testing philosophy

Every detector change should include tests for:

- a case that must be detected;
- a similar case that must not be detected when feasible;
- severity and rule ID stability;
- reporter behavior when the finding affects output formats.

If a rule detects a framework-specific behavior, include a small example that makes the runtime model understandable without requiring a full enterprise application.

## Pull requests

Keep PRs focused. In the description, include:

- problem being solved;
- runtime impact;
- detection strategy;
- known limitations/false positives;
- tests added;
- example output when relevant.

All CI checks should pass before merge.

## Security of dependencies and workflows

RuntimeLens uses Dependabot for Maven and GitHub Actions updates. Third-party actions in project-owned workflows should be pinned to full commit SHAs, with the human-readable release version left in a comment.

Avoid adding a new runtime dependency unless the value clearly outweighs the additional supply-chain surface.

## Design principle

A RuntimeLens warning should feel like an experienced engineer saying:

> "This compiles, but here is what may actually happen when the system runs."

That is the standard we want contributions to maintain.
