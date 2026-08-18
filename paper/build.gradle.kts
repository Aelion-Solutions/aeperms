plugins {
    `java-library`
    id("io.papermc.paperweight.userdev")
    id("com.gradleup.shadow")
}

dependencies {
    implementation(project(":common"))
    paperweight.paperDevBundle("26.2.build.+")
}

tasks {
    jar {
        archiveClassifier.set("thin")
    }

    shadowJar {
        archiveClassifier.set("")
        archiveFileName.set("aeperm-paper-${project.version}.jar")
        dependencies {
            exclude(dependency("com.mojang:brigadier"))
            exclude(dependency("com.zaxxer:HikariCP"))
            exclude(dependency("com.github.ben-manes.caffeine:caffeine"))
        }
        relocate("redis.clients", "sh.aelion.aeperm.libs.jedis")
        relocate("com.google.gson", "sh.aelion.aeperm.libs.gson")
        relocate("org.yaml.snakeyaml", "sh.aelion.aeperm.libs.snakeyaml")
        mergeServiceFiles()
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
    }

    build {
        dependsOn(shadowJar)
    }

    assemble {
        dependsOn(shadowJar)
    }

    processResources {
        val props = mapOf(
            "version" to version.toString(),
            "description" to (project.description ?: "AePerms - A permission plugin for Minecraft servers"),
        )
        filesMatching("paper-plugin.yml") {
            expand(props)
        }
        filesMatching("plugin.yml") {
            expand(props)
        }
    }
}

publishing {
    publications.named<MavenPublication>("maven") {
        artifact(tasks.shadowJar) {
            classifier = "all"
        }
    }
}
