# MultipleTopicsCollaborationAvatarGroup

A Vaadin component that shows the union of participants across multiple
Collaboration Engine topics in a single `AvatarGroup`. A user appears once
they have been registered as a participant in any of the added topics.

## Project Structure

```
multiple-topics-collaboration-avatar-group/       # Reusable component JAR
multiple-topics-collaboration-avatar-group-demo/   # Spring Boot demo app
assembly/                                          # Vaadin Directory packaging
```

## Building

```bash
mvn clean install
```

## Running the Demo

```bash
mvn spring-boot:run -pl multiple-topics-collaboration-avatar-group-demo
```

Open http://localhost:8080/ and log in with one of the demo users:

| Username  | Password  | Display Name      |
|-----------|-----------|-------------------|
| alice     | alice     | Alice Krzykalla   |
| bob       | bob       | Bob Krzykalla     |
| charlie   | charlie   | Charlie Krzykalla |

## Integration Tests

The demo includes a Playwright end-to-end test that logs in all three users,
types in different chat panels, verifies avatar counts, and tests logout cleanup.

Run headless (default):

```bash
mvn verify -pl multiple-topics-collaboration-avatar-group-demo -Pit
```

Run with visible browser windows:

```bash
mvn verify -pl multiple-topics-collaboration-avatar-group-demo -Pit -Dheadless=false
```

Requires `node` and the Playwright npm package (`npm install` in the project root).

## Packaging for Vaadin Directory

```bash
mvn clean install -Pdirectory -pl multiple-topics-collaboration-avatar-group
```

This produces a ZIP file in `multiple-topics-collaboration-avatar-group/target/`
ready for upload to the [Vaadin Directory](https://vaadin.com/directory).
