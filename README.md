# RuntimeLens

[![CI](https://github.com/Shashank022/runtimelens/actions/workflows/ci.yml/badge.svg)](https://github.com/Shashank022/runtimelens/actions/workflows/ci.yml)
[![OpenSSF Scorecard](https://api.scorecard.dev/projects/github.com/Shashank022/runtimelens/badge)](https://scorecard.dev/viewer/?uri=github.com/Shashank022/runtimelens)
[![Java 17+](https://img.shields.io/badge/Java-17%2B-blue)](https://adoptium.net/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

> **Find Java problems that compile successfully but hurt at runtime.**

RuntimeLens is an **IDE-independent Java runtime-risk analyzer**. It looks for code that is syntactically valid and may pass normal unit tests, but can become expensive, misleading, or unsafe when the application runs with real traffic and real data.

It currently focuses on five areas that frequently cause production pain in Java/Spring applications:

- database amplification and ORM access patterns;
- Spring transaction/proxy mistakes;
- asynchronous execution and thread-context boundaries;
- blocking behavior in reactive/WebFlux code;
- unsafe or unbounded executor usage.

RuntimeLens can run from the **terminal, Maven/Gradle build scripts, GitHub Actions, Jenkins, GitLab CI, Azure DevOps, VS Code tasks, IntelliJ, Eclipse, or any environment that can launch Java**. The core engine does not depend on a specific IDE.

**v0.1 is local-first and has zero required third-party runtime dependencies.**

---

## Why RuntimeLens exists

Java compilers answer an important question:

> "Is this code valid Java?"

But production engineers often need a different question answered:

> **"What could this code actually cost or break at runtime?"**

Consider this perfectly valid code:

```java
for (Order order : orders) {
    Customer customer = customerRepository.findById(order.getCustomerId())
            .orElseThrow();
}
```

Nothing is wrong from the compiler's perspective.

But if `orders` contains 10,000 rows, the application may execute roughly 10,000 database calls. The code still compiles. It may even look fine in a test containing five orders.

RuntimeLens is designed to identify that **runtime-risk pattern** before it becomes a production incident.

```text
Source code
    │
    ▼
RuntimeLens
    │
    ├── What pattern did we find?
    ├── Why can it hurt at runtime?
    ├── Where is it located?
    └── What direction should the developer consider?
```

RuntimeLens does **not** try to replace the Java compiler, Sonar-style quality tools, profilers, APM products, or database observability. Its goal is narrower:

> **detect high-value Java/Spring patterns where the source code hides potentially expensive runtime behavior.**

---

## A concrete example

### Before

```java
public List<Customer> customersForOrders(List<Order> orders) {
    List<Customer> customers = new ArrayList<>();

    for (Order order : orders) {
        customerRepository.findById(order.getCustomerId())
                .ifPresent(customers::add);
    }

    return customers;
}
```

### RuntimeLens

```text
HIGH  RL1001  Database query inside loop

customerRepository.findById(order.getCustomerId())

Why this matters:
Database work inside a loop can make the number of queries grow with
input size. A small test dataset may hide a large production cost.

Recommended direction:
Collect the required keys first and prefer a bulk query such as
findAllById(...), an IN query, batching, or a join/projection when the
semantics permit it.
```

### Possible direction

```java
public List<Customer> customersForOrders(List<Order> orders) {
    List<Long> customerIds = orders.stream()
            .map(Order::getCustomerId)
            .distinct()
            .toList();

    return customerRepository.findAllById(customerIds);
}
```

RuntimeLens deliberately says **"possible direction"** rather than blindly rewriting arbitrary application code. Correctness, ordering, transaction semantics, null handling, authorization, and database behavior must be preserved.

---

## Another problem: transaction self-invocation

This looks reasonable:

```java
@Service
public class PaymentService {

    public void checkout() {
        chargeCustomer();
    }

    @Transactional
    public void chargeCustomer() {
        // database work
    }
}
```

The developer may mentally picture:

```text
checkout()
    ↓
chargeCustomer()
    ↓
@Transactional
```

But proxy-based Spring transaction behavior can make internal/self calls surprising.

RuntimeLens reports the pattern so the developer can review whether the call is actually crossing the Spring proxy boundary expected by the design.

```text
PaymentService
    │
    ├── external call → Spring proxy → transactional method
    │
    └── internal self-call ───────────→ review proxy semantics
```

Rule: `RL1004`

---

## Another problem: async + transaction boundaries

```java
@Transactional
public void createOrder(Order order) {
    CompletableFuture.runAsync(() -> repository.save(order));
}
```

The visual source layout can make the asynchronous work look as though it belongs to the same execution context.

At runtime there is a thread boundary:

```text
request thread
    │
    ├── transaction context
    │
    ▼
createOrder()
    │
    └── CompletableFuture.runAsync(...)
                │
                ▼
         another worker thread
                │
                └── repository.save(...)
```

RuntimeLens flags this so the developer explicitly reviews transaction, MDC, tracing, security, tenant, and other thread-local context assumptions.

Rules: `RL1005`, `RL1006`, and `RL1007`.

---

# Quick start

## Requirements

- **Java 17+**
- macOS, Linux, or Windows
- no IDE plugin required

Clone and build:

```bash
git clone https://github.com/Shashank022/runtimelens.git
cd runtimelens
chmod +x build.sh
./build.sh
```

Scan a Java project:

```bash
java -jar dist/runtimelens.jar scan /path/to/java-project
```

Or scan RuntimeLens' intentionally-problematic demo:

```bash
java -jar dist/runtimelens.jar scan examples/demo
```

Windows:

```powershell
.\build.ps1
java -jar dist\runtimelens.jar scan C:\path\to\java-project
```

---

# Commands

### Scan a project

```bash
java -jar dist/runtimelens.jar scan .
```

### Fail CI when a HIGH finding exists

```bash
java -jar dist/runtimelens.jar scan . --fail-on HIGH
```

RuntimeLens returns exit code `2` when the configured severity threshold is reached, which makes it suitable for CI quality gates.

### Run selected rules

```bash
java -jar dist/runtimelens.jar scan . --rules RL1001,RL1004,RL1010
```

### Produce JSON

```bash
java -jar dist/runtimelens.jar scan . \
  --format json \
  --output runtimelens.json
```

### Produce SARIF

```bash
java -jar dist/runtimelens.jar scan . \
  --format sarif \
  --output runtimelens.sarif
```

SARIF makes RuntimeLens suitable for code-scanning systems that understand the standard rather than requiring a proprietary RuntimeLens dashboard.

### List rules

```bash
java -jar dist/runtimelens.jar rules
```

### Understand a rule

```bash
java -jar dist/runtimelens.jar explain RL1004
```

This is intentionally part of the CLI. A useful analysis tool should not merely say "bad code"; it should help the developer understand **why** the pattern matters.

---

# Included rules

| Rule | Severity | Category | Detects |
| --- | --- | --- | --- |
| `RL1001` | HIGH | Database | Database/repository call inside a loop |
| `RL1002` | HIGH | Network | HTTP call inside a loop |
| `RL1003` | MEDIUM | ORM | Possible JPA/Hibernate N+1 getter traversal |
| `RL1004` | HIGH | Spring | `@Transactional` self-invocation risk |
| `RL1005` | HIGH | Concurrency | Async work crossing a transaction boundary |
| `RL1006` | MEDIUM | Concurrency | `CompletableFuture.*Async` using the common pool |
| `RL1007` | MEDIUM | Context | Thread-local/security/request context at an async boundary |
| `RL1008` | HIGH | Reactive | Blocking call in a reactive/WebFlux-like method |
| `RL1009` | HIGH | Concurrency | Potentially unbounded cached thread pool |
| `RL1010` | HIGH | Database | `findAll()` followed by in-memory Java filtering |

See [`docs/RULES.md`](docs/RULES.md) for the compact rule catalog.

---

# The original QueryLens problem RuntimeLens is growing toward

One of the motivating use cases is Java/JPA code that navigates several objects or joined entities:

```java
order.getCustomer()
     .getAddress()
     .getCountry()
     .getRegion()
     .getCode();
```

Or code that loads broad entities and later consumes only a small result shape:

```java
orders.stream()
      .filter(order -> order.getCustomer().getAddress().getState().equals(state))
      .map(order -> new OrderDTO(
          order.getId(),
          order.getCustomer().getName(),
          order.getTotal()))
      .toList();
```

The long-term RuntimeLens query engine is intended to reason about:

```text
Java source
    ↓
Entity relationships
    ↓
Fields actually consumed by the caller
    ↓
Potential generated SQL / access pattern
    ↓
Runtime risk
    ↓
Semantically safer optimization suggestion
```

For example, when enough evidence exists, RuntimeLens may eventually suggest a projection or purpose-built repository query instead of loading a much larger object graph.

The important design principle is:

> **Optimize for the same required result, not merely for shorter-looking Java code.**

A shorter expression is not necessarily a faster query, and an automatic rewrite is not safe unless null semantics, joins, duplicates, sorting, pagination, authorization, transaction behavior, and result shape remain correct.

---

# How RuntimeLens works today

v0.1 intentionally starts with deterministic source heuristics.

```text
                 Java project
                     │
                     ▼
              Source discovery
                     │
                     ▼
                Rule engine
          ┌──────────┼───────────┐
          ▼          ▼           ▼
      Database     Spring    Concurrency
          │          │           │
          └──────────┼───────────┘
                     ▼
                   Finding
                     │
          ┌──────────┼───────────┐
          ▼          ▼           ▼
        Text        JSON        SARIF
          │                       │
          ▼                       ▼
       Terminal              CI / Code Scan
```

The rules use a stable `Rule -> Finding` model, allowing a future semantic frontend to become much smarter without changing every integration.

See [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md).

---

# Where RuntimeLens is going

The project is deliberately staged.

### v0.1 — Runtime-risk signals

- CLI
- 10 deterministic rules
- Text / JSON / SARIF
- CI severity gates
- reusable GitHub Action

### v0.2 — Semantic Java understanding

- AST/symbol resolution
- method and type ownership
- lower false-positive rates
- call relationship analysis

### v0.3 — Spring + JPA intelligence

- Spring bean/proxy graph
- transaction propagation understanding
- JPA entity relationship graph
- repository/query analysis
- result-shape awareness

### v0.4 — Runtime evidence

- JFR integration
- generated SQL correlation
- query-plan evidence
- OpenTelemetry correlation

### v0.5 — PR runtime regression analysis

Conceptually:

```bash
runtimelens diff main..feature
```

with output such as:

```text
Runtime risk comparison

                         main     feature
Database-risk findings      2           5
Async context risks         1           1
Blocking reactive calls     0           2

New HIGH findings: 3
```

The goal is to eventually answer not only **"what is risky?"**, but also **"what runtime risk did this change introduce?"**

---

# GitHub Actions

After a release is tagged, a consuming repository can run RuntimeLens without an IDE plugin:

```yaml
name: RuntimeLens

on:
  pull_request:

permissions:
  contents: read

jobs:
  runtimelens:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v5
      - uses: Shashank022/runtimelens@v0.1.0
        with:
          path: .
          fail-on: HIGH
          sarif: "true"
```

For supply-chain-sensitive production workflows, pin third-party actions—including RuntimeLens—to an immutable commit SHA rather than a movable tag.

---

# Security and supply-chain posture

RuntimeLens is intended to execute against developer source trees, so the project treats its own software supply chain as part of the product.

The repository includes:

- [`SECURITY.md`](SECURITY.md) with private vulnerability-reporting instructions;
- `.github/dependabot.yml` for Maven and GitHub Actions updates;
- OpenSSF Scorecard automation;
- pinned third-party GitHub Action SHAs in RuntimeLens' own workflows;
- least-privilege `GITHUB_TOKEN` permissions;
- generated build/JAR artifacts excluded from source control;
- CI tests on pushes and pull requests.

GitHub's private vulnerability reporting flow is the preferred channel for security problems. **Do not open a public exploit report.**

See [`SECURITY.md`](SECURITY.md) and [`docs/GITHUB_SECURITY_SETUP.md`](docs/GITHUB_SECURITY_SETUP.md).

---

# Privacy model

The RuntimeLens CLI is **local-first**.

Scanning source code does not require sending the project to a RuntimeLens-hosted service. Text, JSON, and SARIF reports are generated locally in the environment where the CLI is executed.

A CI platform may of course retain logs/artifacts according to that platform's own configuration, so teams should treat scan output with the same care they apply to build logs.

---

# What RuntimeLens is not

RuntimeLens v0.1 is **not**:

- a proof that every reported pattern is a production bug;
- a full Java semantic compiler frontend;
- a replacement for profilers/APM tools;
- a database query planner;
- an automatic code-rewriting engine;
- a security guarantee;
- a replacement for code review.

The first release intentionally reports **risk signals**. Developers should review each suggestion in context.

Accuracy and evidence will increase as the semantic and runtime-analysis layers are added.

---

# Building from source

### Simple build

```bash
./build.sh
```

### Windows

```powershell
.\build.ps1
```

### Maven metadata/build

```bash
mvn package
```

The standalone build produces:

```text
dist/runtimelens.jar
```

Generated build artifacts are intentionally ignored by Git.

---

# Contributing

RuntimeLens is designed so new runtime-risk rules can be contributed without changing the reporting stack.

Start with [`CONTRIBUTING.md`](CONTRIBUTING.md).

Good contribution areas include:

- high-confidence Java/Spring runtime patterns;
- false-positive reduction;
- better source evidence;
- Spring/JPA semantics;
- concurrency/thread-context analysis;
- WebFlux/reactive analysis;
- tests and intentionally-problematic examples;
- SARIF/reporting improvements.

A good RuntimeLens rule should answer four questions clearly:

1. **What source pattern did we observe?**
2. **Why can it cause a runtime problem?**
3. **What evidence can we show the developer?**
4. **What safer direction can we suggest without pretending every codebase is identical?**

---

# Project philosophy

RuntimeLens follows five principles:

1. **IDE-independent** — the engine should work everywhere.
2. **Local-first** — source code should not require an external SaaS service to be analyzed.
3. **Deterministic before generative** — start with explainable rules and evidence.
4. **Evidence before auto-fix** — a rewrite is useful only when semantics are preserved.
5. **Teach while detecting** — findings should help developers understand the runtime model, not just silence a linter.

---

# Status

RuntimeLens is an **early pre-1.0 open-source project**. APIs, rule behavior, and command options may evolve while the semantic engine is developed.

Feedback, reproductions, and new rule proposals are welcome.

## License

RuntimeLens is released under the [MIT License](LICENSE).
