plugins {
    `java-library`
    id("io.papermc.paperweight.userdev")
    id("com.gradleup.shadow")
}

dependencies {
    compileOnly(project(":api"))
    paperweight.paperDevBundle("26.2.build.+")
}

tasks {
    jar {
        archiveClassifier.set("thin")
    }

    shadowJar {
        archiveClassifier.set("")
        archiveFileName.set("aeperm-bench-${project.version}.jar")
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
            "description" to "AePerm stress-test plugin (test servers only)"
        )
        filesMatching("plugin.yml") {
            expand(props)
        }
    }
}
