# SOLID — worked examples

Before/after code for each principle. Load this only when the summary in
`SKILL.md` isn't enough; the checklists there are the fast path.

---

## S — Single Responsibility

```java
// ❌ Validation, persistence, notification and audit each give this class a
//    separate reason to change, and none can be tested on its own.
public class UserService {
    public User createUser(String name, String email) {
        if (email == null || !email.contains("@")) {
            throw new IllegalArgumentException("Invalid email");
        }
        User user = new User(name, email);
        entityManager.persist(user);
        emailClient.send(email, "Welcome!", "Hello " + name);
        auditLog.log("User created: " + email);
        return user;
    }
}

// ✅ One responsibility each; the service composes them.
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

```java
// ❌ Every new discount edits this method.
public class DiscountCalculator {
    public double calculate(Order order, String discountType) {
        if (discountType.equals("PERCENTAGE")) {
            return order.getTotal() * 0.1;
        } else if (discountType.equals("LOYALTY")) {
            return order.getTotal() * order.getCustomer().getLoyaltyRate();
        }
        return 0;
    }
}

// ✅ A new discount is a new class.
public interface DiscountStrategy {
    double calculate(Order order);
    boolean supports(String discountType);
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

public class DiscountCalculator {
    private final List<DiscountStrategy> strategies;

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

```java
// ❌ Square breaks the Rectangle contract: setWidth(5); setHeight(4) gives 16.
public class Square extends Rectangle {
    @Override
    public void setWidth(int width) {
        this.width = width;
        this.height = width;   // the caller holding a Rectangle did not ask for this
    }
}

// ✅ Separate abstractions, immutable state — no setter to surprise anyone.
public interface Shape {
    int getArea();
}

public class Rectangle implements Shape {
    private final int width;
    private final int height;

    @Override
    public int getArea() {
        return width * height;
    }
}

public class Square implements Shape {
    private final int side;

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

```java
// ❌ A fat interface forces meaningless implementations.
public interface Worker {
    void work();
    void eat();
    void sleep();
}

public class Robot implements Worker {
    @Override public void work() { /* OK */ }
    @Override public void eat() { /* cannot */ }
    @Override public void sleep() { /* cannot */ }
}

// ✅ Split by role; combine at the implementation.
public interface Workable { void work(); }
public interface Feedable { void eat(); void sleep(); }

public class Employee implements Workable, Feedable { /* ... */ }
public class Robot implements Workable { /* ... */ }
```

This repo's own version is `ColumnarDataSource extends IterableDataSource`: the
uniqueness check depends on the narrower type, so a forward-only source that
would answer `null` for the schema can never be handed in.

---

## D — Dependency Inversion

```java
// ❌ The high-level module names its low-level collaborators: it cannot be
//    tested without a database, and it knows MySQL and SMTP details.
public class OrderService {
    private MySqlOrderRepository repository = new MySqlOrderRepository();
    private SmtpEmailSender emailSender = new SmtpEmailSender();
}

// ✅ Both sides depend on abstractions, injected through the constructor.
public interface OrderRepository { void save(Order order); }
public interface NotificationSender { void send(String recipient, String message); }

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
```

Wiring here is plain constructor injection — no Spring or CDI outside the MCP
adapters — which is what makes the network-off unit tests possible:

```java
// Production
OrderService service = new OrderService(new MySqlOrderRepository(), new SmtpEmailSender());

// Test — the double needs no infrastructure
OrderService service = new OrderService(new InMemoryOrderRepository(), recordingSender);
```
