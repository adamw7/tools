# SOLID — worked examples

Long before/after examples for each principle. Load this only when the summary
in `SKILL.md` isn't enough; the checklists there are the fast path.

---

## S — Single Responsibility

### Violation

```java
// ❌ BAD: UserService does too much
public class UserService {

    public User createUser(String name, String email) {
        // validation logic
        if (email == null || !email.contains("@")) {
            throw new IllegalArgumentException("Invalid email");
        }

        // persistence logic
        User user = new User(name, email);
        entityManager.persist(user);

        // notification logic
        String subject = "Welcome!";
        String body = "Hello " + name;
        emailClient.send(email, subject, body);

        // audit logic
        auditLog.log("User created: " + email);

        return user;
    }
}
```

**Problems:** validation, email template and audit format each give the class a
separate reason to change, and no concern can be tested on its own.

### Refactored

```java
// ✅ GOOD: Each class has one responsibility

public class UserValidator {
    public void validate(String name, String email) {
        if (email == null || !email.contains("@")) {
            throw new ValidationException("Invalid email");
        }
    }
}

public class UserRepository {
    public User save(User user) {
        entityManager.persist(user);
        return user;
    }
}

public class WelcomeEmailSender {
    public void sendWelcome(User user) {
        emailClient.send(user.getEmail(), "Welcome!", "Hello " + user.getName());
    }
}

public class UserAuditLogger {
    public void logCreation(User user) {
        auditLog.log("User created: " + user.getEmail());
    }
}

public class UserService {
    private final UserValidator validator;
    private final UserRepository repository;
    private final WelcomeEmailSender emailSender;
    private final UserAuditLogger auditLogger;

    public User createUser(String name, String email) {
        validator.validate(name, email);
        User user = repository.save(new User(name, email));
        emailSender.sendWelcome(user);
        auditLogger.logCreation(user);
        return user;
    }
}
```

---

## O — Open/Closed

### Violation

```java
// ❌ BAD: Must modify the class to add a new discount type
public class DiscountCalculator {

    public double calculate(Order order, String discountType) {
        if (discountType.equals("PERCENTAGE")) {
            return order.getTotal() * 0.1;
        } else if (discountType.equals("FIXED")) {
            return 50.0;
        } else if (discountType.equals("LOYALTY")) {
            return order.getTotal() * order.getCustomer().getLoyaltyRate();
        }
        return 0;
    }
}
```

### Refactored

```java
// ✅ GOOD: Add new discounts without modifying existing code

public interface DiscountStrategy {
    double calculate(Order order);
    boolean supports(String discountType);
}

public class PercentageDiscount implements DiscountStrategy {
    @Override
    public double calculate(Order order) {
        return order.getTotal() * 0.1;
    }

    @Override
    public boolean supports(String discountType) {
        return "PERCENTAGE".equals(discountType);
    }
}

public class LoyaltyDiscount implements DiscountStrategy {
    @Override
    public double calculate(Order order) {
        return order.getTotal() * order.getCustomer().getLoyaltyRate();
    }

    @Override
    public boolean supports(String discountType) {
        return "LOYALTY".equals(discountType);
    }
}

// A new discount is a new class, not an edit to an existing one
public class SeasonalDiscount implements DiscountStrategy {
    @Override
    public double calculate(Order order) {
        return order.getTotal() * 0.2;
    }

    @Override
    public boolean supports(String discountType) {
        return "SEASONAL".equals(discountType);
    }
}

public class DiscountCalculator {
    private final List<DiscountStrategy> strategies;

    public DiscountCalculator(List<DiscountStrategy> strategies) {
        this.strategies = strategies;
    }

    public double calculate(Order order, String discountType) {
        return strategies.stream()
            .filter(strategy -> strategy.supports(discountType))
            .findFirst()
            .map(strategy -> strategy.calculate(order))
            .orElse(0.0);
    }
}
```

| Pattern | Use when |
|---------|----------|
| Strategy | Multiple algorithms for the same operation |
| Template Method | Same structure, different steps |
| Decorator | Add behaviour around an existing implementation |
| Factory | Create objects without naming the concrete class |

---

## L — Liskov Substitution

### Violation

```java
// ❌ BAD: Square violates the Rectangle contract
public class Rectangle {
    protected int width;
    protected int height;

    public void setWidth(int width) {
        this.width = width;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public int getArea() {
        return width * height;
    }
}

public class Square extends Rectangle {
    @Override
    public void setWidth(int width) {
        this.width = width;
        this.height = width;  // breaks the caller's expectation
    }

    @Override
    public void setHeight(int height) {
        this.width = height;
        this.height = height;
    }
}

// Passes for Rectangle, fails for Square (16, not 20)
void testRectangle(Rectangle rectangle) {
    rectangle.setWidth(5);
    rectangle.setHeight(4);
    assert rectangle.getArea() == 20;
}
```

### Refactored

```java
// ✅ GOOD: Separate abstractions, immutable state

public interface Shape {
    int getArea();
}

public class Rectangle implements Shape {
    private final int width;
    private final int height;

    public Rectangle(int width, int height) {
        this.width = width;
        this.height = height;
    }

    @Override
    public int getArea() {
        return width * height;
    }
}

public class Square implements Shape {
    private final int side;

    public Square(int side) {
        this.side = side;
    }

    @Override
    public int getArea() {
        return side * side;
    }
}
```

| Rule | Meaning |
|------|---------|
| Preconditions | A subtype cannot require more |
| Postconditions | A subtype cannot promise less |
| Invariants | A subtype must maintain the parent's invariants |
| History | A subtype cannot mutate inherited state unexpectedly |

---

## I — Interface Segregation

### Violation

```java
// ❌ BAD: A fat interface forces meaningless implementations
public interface Worker {
    void work();
    void eat();
    void sleep();
    void attendMeeting();
    void writeReport();
}

public class Robot implements Worker {
    @Override public void work() { /* OK */ }
    @Override public void eat() { /* cannot */ }
    @Override public void sleep() { /* cannot */ }
    @Override public void attendMeeting() { /* OK */ }
    @Override public void writeReport() { /* maybe */ }
}
```

### Refactored

```java
// ✅ GOOD: Segregated interfaces, combined where needed

public interface Workable {
    void work();
}

public interface Feedable {
    void eat();
    void sleep();
}

public interface Manageable {
    void attendMeeting();
    void writeReport();
}

public class Employee implements Workable, Feedable, Manageable { /* ... */ }

public class Robot implements Workable {
    @Override public void work() { /* ... */ }
}

public class Intern implements Workable, Feedable { /* ... */ }
```

Splitting a repository interface by use case is the same move:

```java
// ✅ Better than one 20-method Repository<T>
public interface ReadRepository<T> {
    Optional<T> findById(Long id);
    List<T> findAll();
}

public interface WriteRepository<T> {
    T save(T entity);
    void delete(T entity);
}
```

---

## D — Dependency Inversion

### Violation

```java
// ❌ BAD: The high-level module names its low-level collaborators
public class OrderService {
    private MySqlOrderRepository repository;
    private SmtpEmailSender emailSender;

    public OrderService() {
        this.repository = new MySqlOrderRepository();
        this.emailSender = new SmtpEmailSender();
    }

    public void createOrder(Order order) {
        repository.save(order);
        emailSender.send(order.getCustomerEmail(), "Order confirmed");
    }
}
```

**Problems:** cannot be tested without a real database, cannot swap the email
provider, and it knows MySQL and SMTP details it has no business knowing.

### Refactored

```java
// ✅ GOOD: Both sides depend on abstractions

public interface OrderRepository {
    void save(Order order);
    Optional<Order> findById(Long id);
}

public interface NotificationSender {
    void send(String recipient, String message);
}

public class OrderService {
    private final OrderRepository repository;
    private final NotificationSender notificationSender;

    public OrderService(OrderRepository repository, NotificationSender notificationSender) {
        this.repository = repository;
        this.notificationSender = notificationSender;
    }

    public void createOrder(Order order) {
        repository.save(order);
        notificationSender.send(order.getCustomerEmail(), "Order confirmed");
    }
}

public class MySqlOrderRepository implements OrderRepository { /* MySQL specific */ }

public class SmtpEmailSender implements NotificationSender { /* SMTP specific */ }

// The test double needs no infrastructure
public class InMemoryOrderRepository implements OrderRepository {
    private final Map<Long, Order> orders = new HashMap<>();

    @Override
    public void save(Order order) {
        orders.put(order.getId(), order);
    }

    @Override
    public Optional<Order> findById(Long id) {
        return Optional.ofNullable(orders.get(id));
    }
}
```

Wiring, in this repo, is plain constructor injection — there is no Spring or CDI
outside the MCP adapters:

```java
// Production
OrderService service = new OrderService(new MySqlOrderRepository(), new SmtpEmailSender());

// Test
OrderService service = new OrderService(new InMemoryOrderRepository(), recordingSender);
```
