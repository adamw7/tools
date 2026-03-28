# CLAUDE.md

## Project

Java project built with Maven. Run `mvn clean install` from the root to build.

## Java version
Java 25.

## Maven
All versions and scopes are defined only in root pom.xml in dependency management.

## SOLID Principles for Java Development

### Single Responsibility Principle (SRP)
Each class should have one reason to change. Keep classes focused on a single concern — split logic into separate classes rather than overloading one with multiple responsibilities.

### Open/Closed Principle (OCP)
Classes should be open for extension but closed for modification. Use interfaces, abstract classes, and polymorphism to add new behavior without altering existing code.

### Liskov Substitution Principle (LSP)
Subtypes must be substitutable for their base types without breaking correctness. Avoid overriding methods in ways that violate the contract established by the parent class.

### Interface Segregation Principle (ISP)
Prefer small, focused interfaces over large ones. Clients should not be forced to depend on methods they do not use — split broad interfaces into narrower, role-specific ones.

### Dependency Inversion Principle (DIP)
Depend on abstractions, not concrete implementations. High-level modules should not depend on low-level modules; both should depend on interfaces. Use constructor injection to supply dependencies.
