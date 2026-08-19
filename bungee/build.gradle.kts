plugins {
    `java-library`
    id("com.gradleup.shadow")
}

repositories {
    maven("https://oss.sonatype.org/content/repositories/snapshots")
}

dependencies {
    implementation(project(":common"))
    compileOnly("net.md-5:bungeecord-api:1.21-R0.4")
    implementation("net.kyori:adventure-platform-bungeecord:4.3.4")
}

tasks {
    withType<Jar>().configureEach {
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
    }
    shadowJar {
        archiveClassifier.set("")
        archiveFileName.set("aeperm-bungee-${project.version}.jar")
        dependencies {
            exclude(dependency("com.zaxxer:HikariCP"))
            exclude(dependency("com.github.ben-manes.caffeine:caffeine"))
        }
        relocate("redis.clients", "sh.aelion.aeperm.libs.jedis")
        relocate("com.google.gson", "sh.aelion.aeperm.libs.gson")
        relocate("org.yaml.snakeyaml", "sh.aelion.aeperm.libs.snakeyaml")
        relocate("sh.aelion.sql", "sh.aelion.aeperm.libs.sql")
        relocate("sh.aelion.libs.hikari", "sh.aelion.aeperm.libs.hikari")
        relocate("sh.aelion.libs.caffeine", "sh.aelion.aeperm.libs.caffeine")
        mergeServiceFiles()
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
    }
    build {
        dependsOn(shadowJar)
    }
    processResources {
        val props = mapOf(
            "version" to version.toString(),
            "description" to (project.description ?: "AePerms - A permission plugin for Minecraft servers"),
        )
        filesMatching("bungee.yml") {
            expand(props)
        }
    }
}
