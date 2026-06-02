# Project Overview

This is `org.apache.sling.servlets.get`, an OSGi bundle that provides the default GET servlets for Apache Sling. It handles HTML, plain-text, JSON, and XML rendering of Sling resources, plus stream delivery, redirect handling, and Sling/version info endpoints. The bundle ships as a shaded JAR (via `maven-shade-plugin`) that inlines `ISO8601` from `jackrabbit-jcr-commons`. Requires Java 17 and Maven 3.9+.

# Core Commands

```bash
# Build and package
mvn clean package

# Run full test suite
mvn test

# Run a single test class
mvn test -Dtest=JsonRendererServletTest

# Run a single test method
mvn test -Dtest=JsonRendererServletTest#testMethod

# Apply Spotless formatting (inherited from sling-bundle-parent)
mvn spotless:apply

# Check Spotless without modifying files
mvn spotless:check

# Apache RAT license check
mvn apache-rat:check

# Install to local Maven repo
mvn install -DskipTests
```

There is no dev server — this bundle must be deployed to a running Sling instance via the OSGi console or `mvn sling:install`.

# Project Layout

```
pom.xml                    Maven build descriptor
bnd.bnd                    OSGi bundle manifest overrides (optional imports for JCR)
src/
  main/java/org/apache/sling/servlets/get/impl/
    DefaultGetServlet.java      Central dispatcher — routes GET/HEAD by selector/extension
    RedirectServlet.java        Handles jcr:content redirect resources
    SlingInfoServlet.java       Exposes Sling runtime info as JSON
    VersionInfoServlet.java     Exposes JCR version info as JSON
    helpers/
      Renderer.java             Interface implemented by all renderers
      HtmlRenderer.java         HTML output
      JsonRenderer.java         JSON output (uses JsonObjectCreator)
      PlainTextRenderer.java    text/plain output
      XMLRenderer.java          XML output
      StreamRenderer.java       Streams binary resource data
      HeadServletResponse.java  Wraps response to suppress body for HEAD
    util/
      JsonObjectCreator.java    Converts Sling Resources → Jakarta JSON structures
      JsonToText.java           Formats JSON for text/plain rendering
      ResourceTraversor.java    Depth-limited resource tree walker
  test/java/...                 Mirrors main package structure; JUnit 4/Mockito/Sling Mock tests
  test/resources/               Test JSON fixtures (data.json, samplefile.json)
target/                         Build output — do not edit
```

# Development Patterns & Constraints

- **Java 17**, OSGi R7 component model using `org.osgi.service.component.annotations` (`@Component`, `@Activate`, `@Deactivate`, `@Reference`). Never use Felix SCR annotations.
- **OSGi metatype configs**: this codebase also uses `org.osgi.service.metatype.annotations` (`@Designate`, `@ObjectClassDefinition`, `@AttributeDefinition`) for servlet configuration.
- **Jakarta namespace**: the codebase uses `jakarta.servlet.*` and `jakarta.json.*`. Do not mix in `javax.servlet.*` (it is listed as optional/provided for legacy compat only).
- **No public API**: everything lives under `org.apache.sling.servlets.get.impl`. There is no public package export; do not add one without intentional design.
- **Shading**: `org.apache.jackrabbit.util` is relocated to `org.apache.sling.servlets.get.impl.jackrabbit` at package time. Never import the original class name in production code that will run inside the bundle.
- **Code style**: 4-space indentation, no tabs. Spotless enforces formatting (inherited from `sling-bundle-parent`). Run `mvn spotless:apply` before committing.
- **License headers**: All source files must carry the Apache 2.0 license header. RAT checks this on every build.
- **Logging**: use SLF4J 2.x (`org.slf4j`). No `System.out` or `java.util.logging`.
- **Tests**: JUnit 4 + Mockito 3. Sling Mock (sling-mock-oak variant) is available for integration-style tests.

# Git Workflow

- Default branch: `master`.
- Commit messages should reference a JIRA issue where applicable: `SLING-XXXXX Description of change`.
- No force-pushes to `master` (enforced by branch protection).
- Branches are auto-deleted on merge.
- Follow the Apache Sling contribution guide: https://sling.apache.org/contributing.html

# Testing Guidelines

- Framework: **JUnit 4** (`junit:junit`). Do not add JUnit 5 without updating the parent POM.
- Test classes live under `src/test/java/` mirroring the production package path.
- Mocking/test support: **Mockito 3**, **Sling Mock** (`org.apache.sling.testing.sling-mock.junit4` and sling-mock-oak), plus Sling servlet helpers for request/response tests.
- Run all tests: `mvn test`
- Run one class: `mvn test -Dtest=ClassName`
- Reports land in `target/surefire-reports/`.
- Coverage is not configured in this module; add Jacoco to the effective POM if needed.

# Gotchas

- **Shaded JAR vs. plain JAR**: `maven-shade-plugin` runs at `package` phase and produces the final artifact. `target/original-*.jar` is the pre-shade output. Deploy the shaded JAR to OSGi, not the original.
- **Shaded sources are also generated**: both shaded and `original-*` source JARs are produced during packaging.
- **`bnd.bnd` optional imports**: `javax.jcr` is marked `resolution:=optional`. Code that uses JCR must guard against missing packages at runtime.
- **Sling API 3.x**: This bundle targets `org.apache.sling.api` 3.0.0, which uses `SlingJakartaHttpServletRequest`/`SlingJakartaHttpServletResponse`. These differ from the older `SlingHttpServletRequest` — don't mix them.
- **Parent POM version drift**: Many dependency versions (JUnit, Mockito, OSGi annotations) are managed by `sling-bundle-parent`. Check the parent before adding explicit versions.
- **RAT failures**: Adding files without license headers (e.g., test fixtures) will fail the RAT check. Add a RAT exclusion in the parent config or prepend the header.
