# RuntimeLens architecture

```text
                 Java project
                     |
                     v
              Source discovery
                     |
                     v
               Rule engine
         _________|__________
        |         |          |
     database   spring   concurrency
        |         |          |
        +---------+----------+
                  |
                  v
                Finding
                  |
         _________|___________
        |         |           |
       text      JSON        SARIF
        |                      |
     terminal               CI/code scan
```

## Principles
1. IDE-independent.
2. Local-first.
3. Deterministic rules first.
4. Stable `Rule -> Finding` API.
5. Evidence before auto-fix.

## Roadmap
- **v0.1**: CLI, 10 rules, text/JSON/SARIF, GitHub Action.
- **v0.2**: semantic Java frontend with symbol resolution.
- **v0.3**: Spring/JPA bean graph, proxy and query intelligence.
- **v0.4**: JFR, generated SQL and OpenTelemetry correlation.
- **v0.5**: `diff main..feature` and PR regression scoring.
