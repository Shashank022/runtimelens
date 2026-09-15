# RuntimeLens v0.1 rules

| Rule | Severity | Purpose |
|---|---|---|
| RL1001 | HIGH | Database query inside loop |
| RL1002 | HIGH | HTTP call inside loop |
| RL1003 | MEDIUM | Possible ORM N+1 getter traversal |
| RL1004 | HIGH | Spring `@Transactional` self-invocation |
| RL1005 | HIGH | Async work crossing transaction boundary |
| RL1006 | MEDIUM | `CompletableFuture` using the common pool |
| RL1007 | MEDIUM | Thread-local request/security context at async boundary |
| RL1008 | HIGH | Blocking call inside a reactive/WebFlux-like method |
| RL1009 | HIGH | `Executors.newCachedThreadPool()` |
| RL1010 | HIGH | `findAll()` followed by in-memory filtering |

v0.1 reports **risk patterns**, not proof of production behavior. A future semantic engine will add symbol resolution, Spring bean graphs, JPA metadata, generated SQL, call graphs, JFR and OpenTelemetry evidence.
