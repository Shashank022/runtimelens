package io.github.shashank022.runtimelens.rules;

import io.github.shashank022.runtimelens.engine.SourceFile;
import io.github.shashank022.runtimelens.model.Finding;
import io.github.shashank022.runtimelens.model.Severity;
import io.github.shashank022.runtimelens.util.SourceUtils;
import io.github.shashank022.runtimelens.util.SourceUtils.BlockRange;
import io.github.shashank022.runtimelens.util.SourceUtils.MethodRange;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class RuleCatalog {
    private RuleCatalog() {}

    public static List<Rule> defaultRules() {
        return List.of(
                queryInsideLoop(),
                httpInsideLoop(),
                nPlusOneGetterTraversal(),
                transactionSelfInvocation(),
                asyncTransactionBoundary(),
                completableFutureCommonPool(),
                contextLossAcrossAsyncBoundary(),
                blockingCallInReactiveMethod(),
                unboundedExecutor(),
                findAllThenFilter()
        );
    }

    public static Optional<Rule> byId(String id) {
        return defaultRules().stream().filter(r -> r.id().equalsIgnoreCase(id)).findFirst();
    }

    private static Rule queryInsideLoop() {
        return new AbstractRule(
                "RL1001", "Database query inside loop", Severity.HIGH, "database",
                "Database or repository access inside a loop can turn one logical operation into N database round trips.",
                "Collect keys first and use a bulk query such as findAllById(...), an IN query, batching, or a join/projection.") {
            private final Pattern dbCall = Pattern.compile(
                    "\\b([A-Za-z_$][\\w$]*(?:Repository|Repo|Dao)|repository|repo|dao|jdbcTemplate|entityManager)\\s*\\.\\s*" +
                    "(find\\w*|save\\w*|query\\w*|createQuery|createNativeQuery|getReference)\\s*\\(",
                    Pattern.CASE_INSENSITIVE);

            @Override public List<Finding> analyze(SourceFile source) {
                List<Finding> out = new ArrayList<>();
                for (BlockRange loop : SourceUtils.loopBlocks(source)) {
                    for (int line = loop.startLine(); line <= loop.endLine(); line++) {
                        String code = SourceUtils.stripStringsAndLineComment(source.line(line));
                        if (dbCall.matcher(code).find()) {
                            out.add(finding(source, line,
                                    "Database access occurs inside a loop; database calls may grow with collection size."));
                        }
                    }
                }
                return out;
            }
        };
    }

    private static Rule httpInsideLoop() {
        return new AbstractRule(
                "RL1002", "HTTP call inside loop", Severity.HIGH, "network",
                "Synchronous or per-item outbound calls inside a loop multiply latency and can overload downstream services.",
                "Batch the request when supported, prefetch data, or use bounded concurrency with explicit rate limits.") {
            private final Pattern httpCall = Pattern.compile(
                    "\\b(restTemplate|webClient|httpClient|feign\\w*|\\w+FeignClient)\\s*\\.\\s*" +
                    "(getForObject|getForEntity|postForObject|exchange|retrieve|send|execute|get|post|put|delete)\\s*\\(",
                    Pattern.CASE_INSENSITIVE);

            @Override public List<Finding> analyze(SourceFile source) {
                List<Finding> out = new ArrayList<>();
                for (BlockRange loop : SourceUtils.loopBlocks(source)) {
                    for (int line = loop.startLine(); line <= loop.endLine(); line++) {
                        String code = SourceUtils.stripStringsAndLineComment(source.line(line));
                        if (httpCall.matcher(code).find()) {
                            out.add(finding(source, line,
                                    "Outbound HTTP access occurs inside a loop; latency and call volume may scale as O(N)."));
                        }
                    }
                }
                return out;
            }
        };
    }

    private static Rule nPlusOneGetterTraversal() {
        return new AbstractRule(
                "RL1003", "Possible ORM N+1 traversal", Severity.MEDIUM, "database",
                "Deep entity getter traversal while iterating results can trigger lazy association loads and N+1 SQL patterns in JPA/Hibernate.",
                "Review generated SQL. Consider JOIN FETCH, @EntityGraph, batch fetching, or a DTO projection.") {
            private final Pattern getterChain = Pattern.compile(
                    "\\.get[A-Z][A-Za-z0-9_$]*\\s*\\([^)]*\\)\\s*\\.\\s*get[A-Z][A-Za-z0-9_$]*\\s*\\(");

            @Override public List<Finding> analyze(SourceFile source) {
                List<Finding> out = new ArrayList<>();
                for (BlockRange loop : SourceUtils.loopBlocks(source)) {
                    for (int line = loop.startLine(); line <= loop.endLine(); line++) {
                        if (getterChain.matcher(SourceUtils.stripStringsAndLineComment(source.line(line))).find()) {
                            out.add(finding(source, line,
                                    "Deep getter traversal inside an iteration may cross lazy ORM associations."));
                        }
                    }
                }
                return out;
            }
        };
    }

    private static Rule transactionSelfInvocation() {
        return new AbstractRule(
                "RL1004", "Transactional self-invocation", Severity.HIGH, "spring",
                "Spring's proxy-based transaction interception can be bypassed when a method in the same bean calls another @Transactional method directly.",
                "Move the transactional operation to another Spring bean or call it through a proxied collaborator.") {
            @Override public List<Finding> analyze(SourceFile source) {
                List<Finding> out = new ArrayList<>();
                List<MethodRange> methods = SourceUtils.methods(source);
                List<MethodRange> txMethods = methods.stream().filter(m -> m.hasAnnotation("Transactional")).toList();

                for (MethodRange tx : txMethods) {
                    Pattern call = Pattern.compile("(?<![\\w$.])(?:this\\s*\\.\\s*)?" + Pattern.quote(tx.name()) + "\\s*\\(");
                    for (MethodRange caller : methods) {
                        if (caller.startLine() == tx.startLine()) continue;
                        for (int line = caller.startLine() + 1; line <= caller.endLine(); line++) {
                            if (call.matcher(SourceUtils.stripStringsAndLineComment(source.line(line))).find()) {
                                out.add(finding(source, line,
                                        "Call to @Transactional method '" + tx.name() +
                                        "' appears to remain inside the same class and may bypass the Spring proxy."));
                            }
                        }
                    }
                }
                return out;
            }
        };
    }

    private static Rule asyncTransactionBoundary() {
        return new AbstractRule(
                "RL1005", "Async work crosses transaction boundary", Severity.HIGH, "concurrency",
                "Spring transactions are thread-bound. Work moved to another thread does not automatically continue the caller's transaction.",
                "Keep transaction-sensitive DB work on the original thread or start an explicit transaction inside the async worker/service.") {
            private final Pattern asyncApi = Pattern.compile(
                    "CompletableFuture\\s*\\.\\s*(supplyAsync|runAsync)\\s*\\(|\\.\\s*(submit|execute)\\s*\\(");

            @Override public List<Finding> analyze(SourceFile source) {
                List<Finding> out = new ArrayList<>();
                List<MethodRange> methods = SourceUtils.methods(source);
                Set<String> asyncMethods = new HashSet<>();
                for (MethodRange m : methods) if (m.hasAnnotation("Async")) asyncMethods.add(m.name());

                for (MethodRange method : methods) {
                    if (!method.hasAnnotation("Transactional")) continue;
                    for (int line = method.startLine() + 1; line <= method.endLine(); line++) {
                        String code = SourceUtils.stripStringsAndLineComment(source.line(line));
                        if (asyncApi.matcher(code).find()) {
                            out.add(finding(source, line,
                                    "Async execution starts from a @Transactional method; transaction context will not automatically propagate."));
                        }
                        for (String asyncName : asyncMethods) {
                            if (Pattern.compile("(?<![\\w$.])(?:this\\.)?" + Pattern.quote(asyncName) + "\\s*\\(")
                                    .matcher(code).find()) {
                                out.add(finding(source, line,
                                        "An @Async method is invoked from a @Transactional method; it executes with a different thread/transaction context."));
                            }
                        }
                    }
                }
                return out;
            }
        };
    }

    private static Rule completableFutureCommonPool() {
        return new AbstractRule(
                "RL1006", "Implicit CompletableFuture common pool", Severity.MEDIUM, "concurrency",
                "CompletableFuture.*Async overloads without an Executor use the JVM common pool, making resource isolation and tuning harder.",
                "Pass an explicitly sized Executor that is owned by the application and tuned for the workload.") {
            private final Pattern start = Pattern.compile("CompletableFuture\\s*\\.\\s*(supplyAsync|runAsync)\\s*\\(");

            @Override public List<Finding> analyze(SourceFile source) {
                List<Finding> out = new ArrayList<>();
                Matcher matcher = start.matcher(source.text());
                while (matcher.find()) {
                    String invocation = SourceUtils.invocationFromOffset(source.text(), matcher.start());
                    if (SourceUtils.countTopLevelArguments(invocation) == 1) {
                        int line = source.lineOfOffset(matcher.start());
                        out.add(finding(source, line,
                                "CompletableFuture async work uses the shared common pool because no Executor is supplied."));
                    }
                }
                return out;
            }
        };
    }

    private static Rule contextLossAcrossAsyncBoundary() {
        return new AbstractRule(
                "RL1007", "Request context may be lost across async boundary", Severity.MEDIUM, "concurrency",
                "MDC, SecurityContext, tenant/request context and other ThreadLocal state do not automatically move to arbitrary executor threads.",
                "Use a context-propagating TaskDecorator/Executor or explicitly capture and restore only the context that should cross the boundary.") {
            private final Pattern context = Pattern.compile(
                    "\\b(MDC\\.|SecurityContextHolder\\.|TenantContext\\.|RequestContextHolder\\.)");
            private final Pattern async = Pattern.compile(
                    "CompletableFuture\\s*\\.\\s*(supplyAsync|runAsync)\\s*\\(|\\.\\s*(submit|execute)\\s*\\(");

            @Override public List<Finding> analyze(SourceFile source) {
                List<Finding> out = new ArrayList<>();
                for (MethodRange method : SourceUtils.methods(source)) {
                    boolean contextUsed = false;
                    for (int line = method.startLine(); line <= method.endLine(); line++) {
                        if (context.matcher(source.line(line)).find()) { contextUsed = true; break; }
                    }
                    if (!contextUsed) continue;
                    for (int line = method.startLine(); line <= method.endLine(); line++) {
                        if (async.matcher(SourceUtils.stripStringsAndLineComment(source.line(line))).find()) {
                            out.add(finding(source, line,
                                    "This method uses thread-bound context and also dispatches async work; verify context propagation."));
                            break;
                        }
                    }
                }
                return out;
            }
        };
    }

    private static Rule blockingCallInReactiveMethod() {
        return new AbstractRule(
                "RL1008", "Blocking call in reactive method", Severity.HIGH, "reactive",
                "Blocking inside a Reactor/WebFlux execution path can pin event-loop threads and collapse throughput under load.",
                "Use non-blocking APIs, compose the Publisher instead of calling block(), or isolate unavoidable blocking work on a bounded elastic scheduler.") {
            private final Pattern blocking = Pattern.compile(
                    "\\.\\s*(block|blockOptional)\\s*\\(|Thread\\s*\\.\\s*sleep\\s*\\(");

            @Override public List<Finding> analyze(SourceFile source) {
                List<Finding> out = new ArrayList<>();
                for (MethodRange method : SourceUtils.methods(source)) {
                    String decl = method.declaration();
                    boolean reactive = decl.matches("(?s).*\\b(Mono|Flux)\\s*<.*");
                    if (!reactive) continue;
                    for (int line = method.startLine(); line <= method.endLine(); line++) {
                        if (blocking.matcher(SourceUtils.stripStringsAndLineComment(source.line(line))).find()) {
                            out.add(finding(source, line,
                                    "Blocking API detected inside a method that appears to be part of a reactive flow."));
                        }
                    }
                }
                return out;
            }
        };
    }

    private static Rule unboundedExecutor() {
        return new AbstractRule(
                "RL1009", "Potentially unbounded executor", Severity.HIGH, "concurrency",
                "Executors.newCachedThreadPool() can create an unbounded number of threads under sustained load.",
                "Prefer an explicitly bounded ThreadPoolExecutor or framework-managed executor with queue, pool-size, rejection and monitoring policies.") {
            private final Pattern cachedPool = Pattern.compile("Executors\\s*\\.\\s*newCachedThreadPool\\s*\\(");

            @Override public List<Finding> analyze(SourceFile source) {
                List<Finding> out = new ArrayList<>();
                for (int i = 1; i <= source.lines().size(); i++) {
                    if (cachedPool.matcher(SourceUtils.stripStringsAndLineComment(source.line(i))).find()) {
                        out.add(finding(source, i,
                                "newCachedThreadPool() can grow thread count without a configured upper bound."));
                    }
                }
                return out;
            }
        };
    }

    private static Rule findAllThenFilter() {
        return new AbstractRule(
                "RL1010", "findAll() followed by in-memory filtering", Severity.HIGH, "database",
                "Loading all rows and filtering in Java pushes work and network transfer out of the database and into the application.",
                "Move the predicate into a repository/database query and project only the fields needed by the caller.") {
            private final Pattern direct = Pattern.compile(
                    "\\.\\s*findAll\\s*\\(\\s*\\)\\s*\\.\\s*stream\\s*\\(\\s*\\)\\s*\\.\\s*filter\\s*\\(", Pattern.DOTALL);
            private final Pattern assignment = Pattern.compile(
                    "\\b(?:var|List\\s*<[^>]+>|Collection\\s*<[^>]+>|Iterable\\s*<[^>]+>)\\s+" +
                    "([A-Za-z_$][\\w$]*)\\s*=\\s*[^;\\n]*\\.findAll\\s*\\(\\s*\\)\\s*;");

            @Override public List<Finding> analyze(SourceFile source) {
                List<Finding> out = new ArrayList<>();
                Matcher directMatcher = direct.matcher(source.text());
                while (directMatcher.find()) {
                    int line = source.lineOfOffset(directMatcher.start());
                    out.add(finding(source, line,
                            "findAll() is followed by stream().filter(); the predicate likely belongs in the database query."));
                }

                Matcher assignmentMatcher = assignment.matcher(source.text());
                while (assignmentMatcher.find()) {
                    String variable = assignmentMatcher.group(1);
                    int assignmentLine = source.lineOfOffset(assignmentMatcher.start());
                    Pattern later = Pattern.compile("\\b" + Pattern.quote(variable) +
                            "\\s*\\.\\s*stream\\s*\\(\\s*\\)\\s*\\.\\s*filter\\s*\\(", Pattern.DOTALL);
                    Matcher laterMatcher = later.matcher(source.text());
                    laterMatcher.region(assignmentMatcher.end(), source.text().length());
                    if (laterMatcher.find()) {
                        int filterLine = source.lineOfOffset(laterMatcher.start());
                        if (filterLine - assignmentLine <= 80) {
                            out.add(finding(source, assignmentLine,
                                    "Collection '" + variable + "' is populated with findAll() and filtered in memory shortly afterwards."));
                        }
                    }
                }

                Map<String, Finding> unique = new LinkedHashMap<>();
                for (Finding f : out) unique.put(f.file() + ":" + f.line() + ":" + f.ruleId(), f);
                return new ArrayList<>(unique.values());
            }
        };
    }
}
