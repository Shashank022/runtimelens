package io.github.shashank022.runtimelens;

import io.github.shashank022.runtimelens.engine.Analyzer;
import io.github.shashank022.runtimelens.model.Finding;
import io.github.shashank022.runtimelens.rules.RuleCatalog;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;

public final class RuntimeLensTest {
    public static void main(String[] args) throws Exception {
        Path temp = Files.createTempDirectory("runtimelens-test");
        Path source = temp.resolve("Bad.java");
        Files.writeString(source, """
                import java.util.concurrent.*;
                class Bad {
                    @Transactional
                    public void tx() {
                        CompletableFuture.runAsync(() -> save());
                    }
                    public void caller() {
                        tx();
                        Executors.newCachedThreadPool();
                    }
                    void loop(java.util.List<Order> orders) {
                        for (Order o : orders) {
                            orderRepository.findById(o.id());
                            System.out.println(o.getCustomer().getAddress());
                        }
                    }
                    void all() {
                        var items = orderRepository.findAll();
                        items.stream().filter(x -> true).toList();
                    }
                    void save() {}
                }
                @interface Transactional {}
                class Order { long id(){return 1;} Customer getCustomer(){return null;} }
                class Customer { Address getAddress(){return null;} }
                class Address {}
                class orderRepository {
                    static Object findById(long x){return null;}
                    static java.util.List<Object> findAll(){return null;}
                }
                """);

        Analyzer.ScanResult result = new Analyzer(RuleCatalog.defaultRules()).scan(temp);
        Set<String> ids = result.findings().stream().map(Finding::ruleId).collect(Collectors.toSet());

        assertHas(ids, "RL1001");
        assertHas(ids, "RL1003");
        assertHas(ids, "RL1004");
        assertHas(ids, "RL1005");
        assertHas(ids, "RL1006");
        assertHas(ids, "RL1009");
        assertHas(ids, "RL1010");

        System.out.println("RuntimeLens tests passed (" + result.findings().size() + " findings checked).");
    }

    private static void assertHas(Set<String> ids, String id) {
        if (!ids.contains(id)) throw new AssertionError("Expected rule " + id + " but got " + ids);
    }
}
