# AePerm

PEX-style permission core for Beteax / Aelion Cloud.

## Modules

- `aeperm-api` - soft-depend API for other plugins
- `aeperm-common` - engine (storage, calc, cache, ServerSync, commands)
- `aeperm-paper` / `aeperm-velocity` / `aeperm-bungee` - platform jars

## Soft-depend

```kotlin
repositories {
    maven("https://maven.beteax.net/releases") // fill when published
}
dependencies {
    compileOnly("net.beteax:aeperm-api:1.0.0")
}
```

Native `player.hasPermission(...)` works once AePerm is loaded. Use the API to mutate users/groups.

## Build

```bash
./gradlew build
./gradlew test jacocoTestCoverageVerification
```

Paper shadow jar: `paper/build/libs/aeperm-paper-*.jar`
