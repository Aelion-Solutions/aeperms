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
        relocate("com.zaxxer.hikari", "net.beteax.aeperm.libs.hikari")
        relocate("redis.clients", "net.beteax.aeperm.libs.jedis")
        relocate("com.google.gson", "net.beteax.aeperm.libs.gson")
        relocate("org.yaml.snakeyaml", "net.beteax.aeperm.libs.snakeyaml")
        mergeServiceFiles()
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
    }
    build {
        dependsOn(shadowJar)
    }
    processResources {
        val props = mapOf(
            "version" to version.toString(),
            "description" to (project.description ?: "Aelion Permission Plugin")
        )
        filesMatching("bungee.yml") {
            expand(props)
        }
    }
}
