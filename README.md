# AePerm

Lean, low-overhead permission engine for Minecraft servers and networks.

## Modules

- `aeperm-api` - API for other plugins
- `aeperm-common` - core aeperms
- `aeperm-paper` / `aeperm-velocity` / `aeperm-bungee` - platform jars
- `aeperm-bench` - Paper stress-test plugin (test servers only, not published)
- `sql` - vendored [aelion-sql](https://github.com/Aelion-Solutions/aelion-sql)

## Development

Public artifacts live on [maven.aelion.solutions](https://maven.aelion.solutions/#/).

```kotlin
repositories {
    maven("https://maven.aelion.solutions/releases")
    maven("https://maven.aelion.solutions/snapshots")
}

dependencies {
    compileOnly("sh.aelion:aeperm-api:1.1")
}
```

## Building

Java 25, Gradle wrapper:

```bash
./gradlew test jacocoTestCoverageVerification
./gradlew build
```

Jars land in `out/` in root.

## Benchmark plugin

`aeperm-bench` is for test servers only. It mutates AePerm storage. Drop `out/aeperm-bench-*.jar` next to `aeperm-paper` and run as op (or `aeperm.admin`).

`Player.hasPermission` is Bukkit Superperms after AePerm attaches. `AepermAPI.has` is the engine cache-hit path.

| Command | Notes |
|---------|--------|
| `/apbench seed [nodes]` | Isolated `aeperm-bench-*` groups + user nodes (default 500) |
| `/apbench run [iters]` | Bukkit, API, lookup, and recalc microbenches |
| `/apbench bukkit [iters]` | Main-thread `hasPermission` (exact, wildcard, miss) |
| `/apbench api [iters]` | `AepermAPI.has` cache hit |
| `/apbench lookup [iters]` | `CalculatedUser.has` |
| `/apbench recalc [iters]` | Invalidate + `user()` calculate |
| `/apbench tick [perTick] [seconds]` | Per-tick `hasPermission` spam (TPS/MSPT) |
| `/apbench stop` | Cancel tick stress |
| `/apbench cleanup` | Restore primary group and delete bench data |

Alias: `/aepermbench`. Default iters 100000 (cap 1000000). Recalc defaults to 200. Main-thread benches hitch the server on purpose.

## Commands and permissions

Aliases: `/aeperm`, `/ap`
Permission: `aeperm.admin` or op

| Command                                                   | Notes                                                         |
|-----------------------------------------------------------|---------------------------------------------------------------|
| `/ap`                                                     | Usage                                                         |
| `/ap info`                                                | plugin/version/mode Info                                      |
| `/ap user <player\|uuid>`                                 | User info (default)                                           |
| `/ap user <player\|uuid> info`                            | User info                                                     |
| `/ap user <player\|uuid> permissions [page]`              | Effective permissions (page size 8)                           |
| `/ap user <player\|uuid> check <node>`                    | Effective check                                               |
| `/ap user <player\|uuid> permission set <node> [seconds]` | Grant (optional TTL). Prefix `-` to deny                      |
| `/ap user <player\|uuid> permission unset <node>`         | Remove node                                                   |
| `/ap user <player\|uuid> group add <group> [seconds]`     | Add membership (optional TTL)                                 |
| `/ap user <player\|uuid> group remove <group>`            | Remove membership                                             |
| `/ap user <player\|uuid> group primary <group>`           | Set primary group                                             |
| `/ap group list`                                          | List groups)                                                  |
| `/ap group create <name>`                                 | Create group                                                  |
| `/ap group delete <name>`                                 | Delete group (not `default`)                                  |
| `/ap group <name>`                                        | Group info (default)                                          |
| `/ap group <name> info`                                   | Group info                                                    |
| `/ap group <name> permissions [page]`                     | Effective permissions (page size 8)                           |
| `/ap group <name> permission set\|unset <node>`           | Group node                                                    |
| `/ap group <name> parent add\|remove <parent>`            | Inheritance                                                   |
| `/ap group <name> weight <int>`                           | Priority (higher wins later)                                  |
| `/ap cache clear`                                         | Local cache only                                              |
| `/ap cache stats`                                         | User/group cache counts                                       |
| `/ap sync reload`                                         | Local reload and broadcast `RELOAD_ALL` when ServerSync is on |
| `/ap sync status`                                         | Network vs standalone, group counts                           |
| `/ap history [page]`                                      | Recent mutates (API and commands)                             |
| `/ap history user <player\|uuid> [page]`                  | Filter by user                                                |
| `/ap history group <name> [page]`                         | Filter by group                                               |

| Permission     | Default | Notes                          |
|----------------|---------|--------------------------------|
| `aeperm.admin` | op      | All `/ap` / `/aeperm` commands |


Copyright © Aelion Solutions / Variiuz. Licensed under the MIT License.