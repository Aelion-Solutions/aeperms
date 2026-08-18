plugins {
    `java-library`
    id("com.gradleup.shadow")
}

repositories {
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://maven.fabricmc.net/")
}

dependencies {
    implementation(project(":common"))
    compileOnly("com.velocitypowered:velocity-api:3.4.0-SNAPSHOT")
    annotationProcessor("com.velocitypowered:velocity-api:3.4.0-SNAPSHOT")
}

tasks {
    withType<Jar>().configureEach {
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
    }
    shadowJar {
        archiveClassifier.set("")
        archiveFileName.set("aeperm-velocity-${project.version}.jar")
        dependencies {
            exclude(dependency("com.mojang:brigadier"))
            exclude(dependency("com.zaxxer:HikariCP"))
            exclude(dependency("com.github.ben-manes.caffeine:caffeine"))
        }
        relocate("redis.clients", "sh.aelion.aeperm.libs.jedis")
        relocate("com.google.gson", "sh.aelion.aeperm.libs.gson")
        relocate("org.yaml.snakeyaml", "sh.aelion.aeperm.libs.snakeyaml")
        // Hikari + Caffeine already relocated inside aelion-sql to sh.aelion.libs.*
        mergeServiceFiles()
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
    }
    build {
        dependsOn(shadowJar)
    }
    processResources {
        val props = mapOf("version" to version.toString())
        filesMatching("velocity-plugin.json") {
            expand(props)
        }
    }
}
