# Keycloak Testcontainer Versions

## Testcontainers & Keycloak version compatibility

| Testcontainers-Keycloak | Testcontainers | Keycloak |
|-------------------------|----------------|----------|
| 4.1.x                   | 2.0.3          | 26.5     |
| 4.0.x (Docker 29+)      | 2.0.2          | 26.4     |
| 3.9.x                   | 1.21.3         | 26.4     |
| 3.8.x                   | 1.21.3         | 26.3     |
| 3.7.x                   | 1.20.6         | 26.2     |
| 3.6.x                   | 1.20.4         | 26.1     |
| 3.5.x                   | 1.20.2         | 26.0     |
| 3.4.x                   | 1.19.8         | 25.0     |
| 3.3.x                   | 1.19.6         | 24.0     |
| 3.2.x                   | 1.19.3         | 23.0     |
| 3.1.x                   | 1.18.3         | 22.0.5   |
| 3.0.x                   | 1.18.3         | 22.0     |
| 2.6.0                   | 1.18.3         | 22.0     |
| 2.5.0                   | 1.17.6         | 21.0     |
| 2.4.0                   | 1.17.3         | 20.0.0   |
| 2.3.0                   | 1.17.1         | 19.0.0   |
| 2.2.2                   | 1.17.1         | 18.0.0   |
| 2.2.1                   | 1.17.1         | 18.0.0   |
| 2.2.0                   | 1.17.1         | 18.0.0   |
| 2.1.2                   | 1.16.3         | 17.0.1   |
| 2.1.1                   | 1.16.3         | 17.0.0   |
| 2.0.0 (Quarkus-based)   | 1.16.3         | 17.0.0   |
| 1.10.0                  | 1.16.3         | 17.0.0   |
| 1.9.0                   | 1.16.2         | 16.0.0   |
| 1.8.0                   | 1.15.3         | 15.0.2   |
| 1.7.1                   | 1.15.3         | 13.0.1   |
| 1.7.0                   | 1.15.3         | 13.0.0   |
| 1.6.1                   | 1.15.1         | 12.0.4   |
| 1.6.0                   | 1.15.1         | 12.0.1   |
| 1.5.0                   | 1.15.1         | 12.0.1   |
| 1.4.0                   | 1.13.0         | 11.0.2   |
| 1.3.3                   | 1.13.0         | 10.0.2   |
| 1.3.1                   | 1.13.0         | 9.0.2    |
| 1.3.0                   | 1.12.3         | 8.0.1    |
| 1.2.0                   | 1.12.3         | 8.0.1    |

_There might also be other possible version configurations which will work._

See also the [Releases](https://github.com/dasniko/testcontainers-keycloak/releases) page for version and feature update notes.

## Public Repository

The release versions of this project are available at [Maven Central](https://central.sonatype.com/artifact/com.github.dasniko/testcontainers-keycloak).
Simply put the dependency coordinates to your `pom.xml` (or something similar, if you use e.g. Gradle or something else):

```xml
<dependency>
  <groupId>com.github.dasniko</groupId>
  <artifactId>testcontainers-keycloak</artifactId>
  <version>VERSION</version>
  <scope>test</scope>
</dependency>
```

There is also a `999.0.0-SNAPSHOT` version available, pointing to `nightly` Docker image by default and using the `999.0.0-SNAPSHOT` Keycloak libraries as dependencies.
