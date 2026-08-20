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
        relocate("sh.aelion.sql", "sh.aelion.aeperm.libs.sql")
        relocate("sh.aelion.libs.hikari", "sh.aelion.aeperm.libs.hikari")
        relocate("sh.aelion.libs.caffeine", "sh.aelion.aeperm.libs.caffeine")
        relocate("org.hibernate", "sh.aelion.aeperm.libs.hibernate")
        relocate("org.flywaydb", "sh.aelion.aeperm.libs.flyway")
        relocate("net.bytebuddy", "sh.aelion.aeperm.libs.bytebuddy")
        relocate("org.jboss.logging", "sh.aelion.aeperm.libs.jboss.logging")
        relocate("jakarta.persistence", "sh.aelion.aeperm.libs.jakarta.persistence")
        relocate("jakarta.transaction", "sh.aelion.aeperm.libs.jakarta.transaction")
        relocate("org.antlr", "sh.aelion.aeperm.libs.antlr")
        relocate("org.slf4j", "sh.aelion.aeperm.libs.slf4j")
        mergeServiceFiles()
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
    }
    build {
        dependsOn(shadowJar)
    }
}
