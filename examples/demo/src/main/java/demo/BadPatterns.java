package demo;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

// Intentionally contains anti-patterns for the RuntimeLens demo.
public class BadPatterns {
    private OrderRepository orderRepository;
    private RestTemplate restTemplate;

    public void queryInsideLoop(List<Order> orders) {
        for (Order order : orders) {
            orderRepository.findById(order.getId());
            System.out.println(order.getCustomer().getAddress().getState());
        }
    }

    public void httpInsideLoop(List<String> ids) {
        for (String id : ids) {
            restTemplate.getForObject("/customers/" + id, Object.class);
        }
    }

    @Transactional
    public void checkout() {
        CompletableFuture.runAsync(() -> orderRepository.save(new Order()));
    }

    @Transactional
    public void saveOrder() {
        orderRepository.save(new Order());
    }

    public void callInsideSameBean() {
        saveOrder();
    }

    public void sharedPool() {
        CompletableFuture.supplyAsync(() -> "hello");
    }

    public void unboundedPool() {
        Executors.newCachedThreadPool();
    }

    public void filterEverythingInMemory() {
        var all = orderRepository.findAll();
        all.stream().filter(order -> order.getId() > 100).toList();
    }

    interface OrderRepository {
        Object findById(long id);
        void save(Order order);
        List<Order> findAll();
    }

    static class RestTemplate {
        Object getForObject(String uri, Class<?> type) { return null; }
    }

    static class Order {
        long getId() { return 0; }
        Customer getCustomer() { return new Customer(); }
    }

    static class Customer { Address getAddress() { return new Address(); } }
    static class Address { String getState() { return "AR"; } }
    @interface Transactional {}
}
