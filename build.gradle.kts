plugins {
    java
    `java-library`
    `maven-publish`
    jacoco
    id("com.gradleup.shadow") version "9.6.1" apply false
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.21" apply false
}

allprojects {
    group = project.findProperty("group") as String
    version = project.findProperty("version") as String
    description = project.findProperty("description") as String

    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://libraries.minecraft.net")
    }
}

subprojects {
    apply(plugin = "java-library")
    apply(plugin = "maven-publish")
    apply(plugin = "jacoco")

    java {
        toolchain.languageVersion = JavaLanguageVersion.of(25)
        withSourcesJar()
        withJavadocJar()
    }

    dependencies {
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release = 25
    }

    tasks.withType<Javadoc>().configureEach {
        options {
            (this as StandardJavadocDocletOptions).addBooleanOption("Xdoclint:none", true)
        }
        isFailOnError = false
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }

    tasks.jacocoTestReport {
        dependsOn(tasks.test)
        reports {
            xml.required = true
            html.required = true
        }
    }

    publishing {
        publications {
            create<MavenPublication>("maven") {
                from(components["java"])
                artifactId = "aeperm-${project.name}"
            }
        }
        repositories {
            maven {
                name = "beteax"
                url = uri(System.getenv("MAVEN_URL") ?: "https://maven.beteax.net/releases")
                credentials {
                    username = System.getenv("MAVEN_USER") ?: ""
                    password = System.getenv("MAVEN_PASSWORD") ?: ""
                }
            }
        }
    }
}

tasks.register("testCoverage") {
    dependsOn(":api:jacocoTestCoverageVerification", ":common:jacocoTestCoverageVerification")
}
