# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a Kotlin Spring Boot multi-module template using Gradle with convention plugins. The project demonstrates a modular architecture approach where common build configuration is centralized in `buildSrc` as reusable convention plugins.

## Architecture

### Multi-Module Structure

The project contains two independent Spring Boot modules:
- **projectA**: Spring Boot application with JPA and PostgreSQL
- **ProjectB**: Spring Boot application with JPA and PostgreSQL

Each module is a fully independent microservice with its own:
- Application entry point (`ProjectAApplication.kt` / `ProjectBApplication.kt`)
- Configuration (application-*.yml files for local, dev, prod profiles)
- Test infrastructure (`SpringBootTestSupport.kt`)

### Convention Plugin System

The build system uses a layered convention plugin approach in `buildSrc/src/main/kotlin/`:

1. **yebali.kotlin** (base layer)
   - Kotlin JVM, Spring, and JPA plugins
   - Java 21 compatibility
   - ktlint 1.8.0 with disabled rules (max-line-length, multiline-expression-wrapping, etc.)
   - Kotlin coroutines and kotlin-logging-jvm (io.github.oshai) dependencies
   - JPA allOpen annotations
   - Kotlin 2.x compilerOptions DSL (freeCompilerArgs)

2. **yebali.kotlin.spring-web** (extends yebali.kotlin)
   - Spring Boot and dependency management
   - Web, validation, actuator starters
   - Jackson Kotlin module
   - SpringMockK for testing (instead of Mockito)
   - Spring Cloud BOM

3. **yebali.kotlin.spring-web.jpa** (extends yebali.kotlin.spring-web)
   - Spring Data JPA
   - QueryDSL 7.4 (OpenFeign fork: io.github.openfeign.querydsl) with KAPT annotation processing
   - H2 test database

4. **yebali.kotlin.spring-web.jpa.postgresql** (extends yebali.kotlin.spring-web.jpa)
   - PostgreSQL driver

**Version Management**: All versions are centralized in `gradle/libs.versions.toml`. The `VersionCatalog.kt` utility provides extension properties to access versions in convention plugins.

**QueryDSL Setup**: Each module includes QueryDslConfig to configure JPAQueryFactory for type-safe JPA queries.

## Common Commands

### Build and Test
```bash
# Build all modules
./gradlew build

# Build specific module
./gradlew :projectA:build
./gradlew :ProjectB:build

# Run tests for all modules
./gradlew test

# Run tests for specific module
./gradlew :projectA:test
./gradlew :ProjectB:test

# Run a single test class
./gradlew :projectA:test --tests "com.yebali.template.SomeTest"

# Run a single test method
./gradlew :projectA:test --tests "com.yebali.template.SomeTest.testMethodName"

# Clean build
./gradlew clean build
```

### Linting
```bash
# Check code style
./gradlew ktlintCheck

# Auto-format code
./gradlew ktlintFormat

# Format specific module
./gradlew :projectA:ktlintFormat
./gradlew :ProjectB:ktlintFormat
```

### Running Applications
```bash
# Run projectA
./gradlew :projectA:bootRun

# Run ProjectB
./gradlew :ProjectB:bootRun

# Run with specific profile
./gradlew :projectA:bootRun --args='--spring.profiles.active=local'
```

### Creating JAR
```bash
# Create executable JAR
./gradlew bootJar

# Build OCI image (Jib is available but not configured in modules yet)
./gradlew bootBuildImage
```

## Development Notes

- **Spring Profiles**: Each module has profiles for `local`, `dev`, `prod`, and `test`
- **Testing Framework**: Uses SpringMockK 5.x instead of Mockito. Extend `SpringBootTestSupport` for integration tests. Integration tests run against a real PostgreSQL via Testcontainers (singleton container + `@ServiceConnection`), so **`./gradlew test` requires a running Docker daemon**
- **Database**: PostgreSQL for production and tests (tests use Testcontainers `postgres:18-alpine`)
- **Kotlin Version**: 2.4.0 with coroutines 1.11.0 support
- **Spring Boot Version**: 4.0.7 (Spring Framework 7 기반)
- **Spring Cloud Version**: 2025.1.2 (Oakwood)
- **Java Version**: 21
- **Gradle Version**: 9.6.0
- **Logging**: kotlin-logging-jvm 8.0.4 (io.github.oshai) - package changed from `mu.*` to `io.github.oshai.kotlinlogging.*`
- **QueryDSL**: 7.4 from OpenFeign fork (io.github.openfeign.querydsl) - original com.querydsl is no longer maintained
