# platform/

Shared libraries every service depends on. They are **libraries, not services** — nothing here
runs on its own, and none of them know anything about chess rules or any service's domain.

| Module | Provides | Key types |
| --- | --- | --- |
| `common-events` | The published event contract | `EventEnvelope`, `EventType`, `payload/*` |
| `common-security` | Internal JWT resource-server security | `JwtSecurityConfig`, `CurrentUser` |
| `common-messaging` | Rabbit topology, transactional outbox, idempotent consumption | `OutboxPublisher`, `OutboxRelay`, `IdempotentEventProcessor`, `RabbitNames` |

## Wiring a service to the platform

Because these are libraries, a service must opt in to scanning them. On the service's
`@SpringBootApplication` class:

```java
@SpringBootApplication(scanBasePackages = {
        "com.ccmmd.chess.gameservice",
        "com.ccmmd.chess.platform"
})
@EntityScan({
        "com.ccmmd.chess.gameservice",
        "com.ccmmd.chess.platform.messaging"
})
@EnableJpaRepositories({
        "com.ccmmd.chess.gameservice",
        "com.ccmmd.chess.platform.messaging"
})
public class GameServiceApplication { ... }
```

Entity scanning is declared explicitly rather than contributed by an auto-configuration: two
`@EntityScan` sources silently replace each other, and a service whose own entities vanish at
runtime is a nasty way to find that out.

## Rules

1. **Publish inside the transaction.** `OutboxPublisher.publish` is `Propagation.MANDATORY` on
   purpose. It throws if there is no transaction — that is the guard rail, not a bug.
2. **Consume through `IdempotentEventProcessor`.** Delivery is at-least-once; handlers must be
   safe to run twice, and this makes them so.
3. **Never add a domain type to `common-events`.** Payloads are primitive records; adding a
   service's entity would couple every consumer to its schema.
4. **Only ever add event types.** The `EventType` strings are routing keys and consumer
   discriminators; renaming one silently breaks consumers.
