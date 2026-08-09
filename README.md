# cqrs-4-springboot

The Spring Boot flavour of [cqrs-4-java](https://github.com/fuinorg/cqrs-4-java) — everything a Spring
Boot application needs to run the CQRS base classes, and nothing that is not Spring specific.

Split out of the cqrs-4-java repository so the two framework flavours can move at their own pace. **The
coordinates did not change:** every artifact keeps its `org.fuin.cqrs4j` groupId and its
`cqrs-4-java-springboot-*` artifactId. A consumer sees one more BOM to import and nothing else.

## Using it

Import both BOMs — this one for what is built here, cqrs-4-java's for `core`, `esc`, `jackson`, `jsonb`,
`jpa` and `test-helper` — then declare the modules you need without versions:

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.fuin.cqrs4j</groupId>
            <artifactId>cqrs-4-java-bom</artifactId>
            <version>${cqrs4j.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
        <dependency>
            <groupId>org.fuin.cqrs4j</groupId>
            <artifactId>cqrs-4-java-springboot-bom</artifactId>
            <version>${cqrs4j-springboot.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

## Modules

Every module has a `README.md` of its own; this is the map.

| Module | |
|---|---|
| [`common`](common) | Thread-local and tenant-context cleanup between requests. |
| [`command-core`](command-core) + [`command-starter`](command-starter) | The write side — the REST controller that accepts commands, the dispatcher, the exception handlers and the caller's execution context — and the auto-configuration that exposes it. |
| [`query-core`](query-core) + [`query-starter`](query-starter) | The read side: projections with their leases, positions and lag metrics, plus the data source that routes per tenant. |
| [`process-manager`](process-manager) | `cqrs-4-java-springboot-pm-core`: the outbox that delivers commands over REST, the timeout sweeper and the metrics of both. |
| [`keycloak-core`](keycloak-core) + [`keycloak-starter`](keycloak-starter) | Multi-tenant Keycloak token *validation*: tenant repositories, the per-realm key selector, the issuer and audience validators, the role converter. |
| [`security`](security) | The filter chain that *decides access*, from `cqrs4j.security.*`. Add it and write YAML — see its README. |

Not published: [`jacoco`](jacoco) aggregates the coverage report, and [`test`](test) is the sample
application the integration tests drive.

## Building

Requires JDK 25 and a container runtime for the integration tests.

```bash
./mvnw clean verify -s settings.xml
```

`-s settings.xml` adds the snapshot repository every `org.fuin` dependency resolves from; without it the
build fails on the first snapshot.
