# Virtual Clubroom — backend

Spring Boot 3.5 (LTS line of Spring Boot 3), Java 21, modular monolith checked by Spring Modulith.

## Prerequisites

- **JDK 21** (`pom.xml` sets `java.version` 21; JDK 17 fails with `release version 21 not supported`)
- Docker (for PostgreSQL, Redis and the integration tests)

Check the JDK Maven will use, and point `JAVA_HOME` at a JDK 21 if needed:

```bash
java -version                       # must report 21.x
export JAVA_HOME=/path/to/jdk-21    # only if the default java is not 21
```

## Run

```bash
cp ../.env.example ../.env          # fill FT_CLIENT_ID / FT_CLIENT_SECRET for 42 login
docker compose -f ../compose.yaml -f ../compose.dev.yaml up -d
./mvnw spring-boot:run
```

- API docs: http://localhost:8080/swagger-ui.html
- Health: http://localhost:8080/actuator/health
- Current user: `GET /api/me` (401 until logged in through `/oauth2/authorization/42`)

Without a 42 OAuth app the app still starts, but the 42 login fails.

## Test

```bash
./mvnw verify
```

- `ModularityTests` checks the module boundaries and writes PlantUML docs to `target/spring-modulith-docs`.
- `ClubroomApplicationTests` boots the full context against PostgreSQL and Redis containers (Docker required).

## Build an image

```bash
docker build -t clubroom-backend .
```

## Layout

```
src/main/java/pt/vclubroom/
├── <module>/            one package per module (club, membership, forum, …)
│   ├── api/             public interface other modules may use
│   ├── events/          domain events other modules may listen to
│   └── spi/             interfaces other modules implement (where needed)
└── shared/              shared kernel (config, clock, cross-cutting types)
src/main/resources/db/migration/<module>/   Flyway migrations, V<yyyyMMddHHmm>__<description>.sql
```

Rules (enforced by `ModularityTests`):

- Never import another module's internal package, only its `api`, `events` or `spi` package.
- Never edit a migration that has already been merged; add a new one.
- Hibernate never changes the schema (`ddl-auto: validate`); Flyway owns it.

## Configuration

All environment-specific values come from environment variables; see [`../.env.example`](../.env.example).
`application.yml` documents each one.
