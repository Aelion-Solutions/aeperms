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
        }
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

    assemble {
        dependsOn(shadowJar)
    }

    processResources {
        val props = mapOf(
            "version" to version.toString(),
            "description" to (project.description ?: "Aelion Permission Plugin")
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
