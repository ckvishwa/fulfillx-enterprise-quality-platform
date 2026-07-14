# ADR-001: Initial Architecture and Technology Choices

- **Status:** Accepted
- **Date:** 2026-07-13

## Context

FulfillX needed a starting architecture and a locked technology stack
before any business code could be written, per the project's Phase 0
mandate. Two decisions in particular carried real risk and are recorded
here with their reasoning, since they diverge from what a naive reading of
the original build brief might suggest.

## Decision 1: Spring Boot 4.1.0, not a 3.x version

The build brief specified "Spring Boot" without a version. At the time of
this decision (2026-07-13), research showed:

- Spring Boot 3.5 (the last 3.x line) reached **open-source end-of-life on
  2026-06-30** — 13 days before this decision. Its final OSS release was
  3.5.16.
- Spring Boot 4.1.0 (released 2026-06-10) is the current actively
  supported release, with support through 2027-07-31. Spring Boot 4.0 is
  also still supported (through 2026-12-31).

Building a "production-style" portfolio project on a dead framework branch
that receives no further CVE patches would undermine the project's own
stated security-baseline requirements. Spring Boot 4.1.0 was chosen instead
of the newer-but-riskier alternative of tracking `master`/milestone builds.

**Consequences accepted:**
- Flyway migrations require the `spring-boot-starter-flyway` dependency
  explicitly — Spring Boot 4.x no longer auto-configures Flyway from
  `flyway-core` alone.
- Testcontainers is pulled in at 2.0.5 via Spring Boot's managed BOM, which
  renamed several artifacts (see ADR consequence below and `CLAUDE.md`
  section 3). Code and POMs must use the new coordinates
  (`testcontainers-junit-jupiter`, `testcontainers-postgresql`, etc.), not
  the pre-2.x ones found in most existing tutorials/blog posts.
- Hibernate ORM 7.1 / Jakarta Persistence 3.2 / Jakarta EE 11 baseline
  apply; most JPA code is unaffected, but this should be kept in mind if
  copying older Hibernate-specific snippets from pre-Boot-4 sources.

## Decision 2: Maven Wrapper instead of requiring a system Maven install

The initial environment audit found Java 21, Docker, Docker Compose,
Node.js, npm, and Git available, but **no Maven on `PATH`**. Rather than
blocking on a manual system install, the Maven Wrapper (`mvnw`/`mvnw.cmd` +
`.mvn/wrapper/`) was generated from the official
`org.apache.maven.wrapper:maven-wrapper-distribution:3.3.4` binary
distribution and configured to bootstrap Apache Maven 3.9.16. This was
verified working (`./mvnw -N -version` successfully downloaded and ran
Maven 3.9.16 against the local Java 21.0.4 JDK) before any POM was written.

**Consequence:** every build command in this repository and its CI
pipeline uses `./mvnw`, never a bare `mvn`. This is also simply better
practice for build reproducibility across contributor machines.

## Decision 3: Root `pom.xml` is a plain aggregator, not a Spring parent

The root `pom.xml` has `packaging=pom` and lists `applications/order-service`
as a module, but does **not** act as `order-service`'s Maven `<parent>`.
`order-service` inherits directly from `spring-boot-starter-parent`. This
is deliberate: future `quality-platform/*` modules (REST Assured, Playwright
driver glue, Pact, k6 wrappers) are not Spring Boot applications and must
not inherit Spring Boot's dependency management. The aggregator's only job
is to let `./mvnw` build the whole reactor from the repo root.

## Decision 4: First test targets real PostgreSQL via Testcontainers, not H2

The build brief prohibits using H2 as the *only* proof of PostgreSQL
behavior anywhere in the project, but doesn't mandate what the very first
test looks like. Rather than writing a trivial Spring context-load test now
and deferring real infrastructure testing to Phase 4, the first test
(`OrderPersistenceIntegrationTest`) was written against a real
Testcontainers-managed PostgreSQL 16 container from the start, proving both
that the Flyway migration applies cleanly and that the
duplicate-idempotency-key protection (RISK-02) actually works at the
database layer. This sets the right precedent early instead of requiring a
rewrite in Phase 4.

## Decision 5: PostgreSQL 18 volume mount path

The `postgres:18-alpine` image changed its expected volume mount point:
Postgres 18+ images expect a single volume at `/var/lib/postgresql` (not
`/var/lib/postgresql/data` as in prior major versions), to support
`pg_ctlcluster`-style in-place major-version upgrades. `docker-compose.yml`
mounts `postgres-data` at `/var/lib/postgresql` accordingly. This was
discovered by an actual failed container start during Phase 1 validation,
not assumed — see `docker compose logs postgres` output referenced in the
phase completion report.

## Alternatives considered and rejected

- **Staying on Spring Boot 3.5.x** for ecosystem maturity — rejected due to
  its OSS EOL status as of this decision date (see Decision 1).
- **Deferring real Postgres testing to Phase 4 with a trivial first test
  now** — rejected in favor of Decision 4's approach, since Docker was
  already confirmed available and the extra cost was minimal.
