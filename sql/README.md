# AePerm SQL package

Vendored copy of [Aelion-Solutions/aelion-sql](https://github.com/Aelion-Solutions/aelion-sql).

AePerm keeps this module as a small internal library (`api(project(":sql"))`) so plugin jars do not depend on the private Maven artifact. Canonical source, cache/query expansions, and Maven publishes (`sh.aelion:aelion-sql`) live in that repo: `private` / `private-snapshots` on maven.aelion.solutions.

This copy is shaded into the platform jars. Source stays `sh.aelion.sql`. Paper, Velocity, and Bungee relocate it to `sh.aelion.aeperm.libs.sql` (and Hikari/Caffeine to `sh.aelion.aeperm.libs.hikari` / `sh.aelion.aeperm.libs.caffeine`) so the plugin jars cannot collide with canonical `sh.aelion:aelion-sql`. It is a slimmed-down snapshot used for permission storage and is not auto-synced with the canonical library. The copies will drift.

Do not publish this module from AePerm.
