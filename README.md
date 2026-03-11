# MultipleTopicsCollaborationAvatarGroup

A Vaadin component that shows the union of participants across multiple
Collaboration Engine topics in a single `AvatarGroup`. A user appears once
they have been registered as a participant in any of the added topics.

## Building

```bash
mvn clean install
```

## Running the Demo

```bash
mvn spring-boot:test-run
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
mvn verify -Pit
```

Run with visible browser windows:

```bash
mvn verify -Pit -Dheadless=false
```

Requires `node` and the Playwright npm package (`npm install` in the project root).

## Packaging for Vaadin Directory

```bash
mvn clean install -Pdirectory
```

This produces a ZIP file in `target/` ready for upload to the
[Vaadin Directory](https://vaadin.com/directory).
