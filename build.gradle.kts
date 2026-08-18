plugins {
    java
    `java-library`
    `maven-publish`
    jacoco
    idea
    id("com.gradleup.shadow") version "9.6.1" apply false
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.21" apply false
    id("io.freefair.lombok") version "9.5.0" apply false
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
    apply(plugin = "io.freefair.lombok")
    apply(plugin = "idea")

    java {
        toolchain.languageVersion = JavaLanguageVersion.of(25)
        withSourcesJar()
        withJavadocJar()
    }

    dependencies {
        "testImplementation"(platform("org.junit:junit-bom:5.13.4"))
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release = 25
    }

    tasks.named<org.gradle.api.tasks.bundling.Jar>("jar").configure {
        when (project.name) {
            "api", "common" -> archiveBaseName.set("aeperm-${project.name}")
            "sql" -> archiveBaseName.set("aelion-sql")
        }
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

    if (name == "bench") {
        tasks.withType<PublishToMavenRepository>().configureEach { enabled = false }
        tasks.withType<PublishToMavenLocal>().configureEach { enabled = false }
    }

    publishing {
        publications {
            create<MavenPublication>("maven") {
                from(components["java"])
                artifactId = if (project.name == "sql") "aelion-sql" else "aeperm-${project.name}"
                pom {
                    name.set(if (project.name == "sql") "Aelion SQL" else "AePerm ${project.name}")
                    description.set(project.description)
                    url.set("https://github.com/Aelion-Solutions/aeperms")
                    developers {
                        developer {
                            name.set("Variiuz")
                            organization.set("Aelion Solutions")
                        }
                    }
                    scm {
                        url.set("https://github.com/Aelion-Solutions/aeperms")
                        connection.set("scm:git:https://github.com/Aelion-Solutions/aeperms.git")
                    }
                }
            }
        }
        repositories {
            maven {
                name = "aelion"
                val repo = if (version.toString().endsWith("SNAPSHOT", ignoreCase = true)) {
                    "snapshots"
                } else {
                    "releases"
                }
                url = uri("https://maven.aelion.solutions/$repo")
                credentials {
                    username = providers.gradleProperty("aelion.maven.user")
                        .orElse(providers.environmentVariable("MVN_USER"))
                        .orElse(providers.environmentVariable("MAVEN_USER"))
                        .orElse("")
                        .get()
                    password = providers.gradleProperty("aelion.maven.password")
                        .orElse(providers.environmentVariable("MVN_TOKEN"))
                        .orElse(providers.environmentVariable("MAVEN_PASSWORD"))
                        .orElse("")
                        .get()
                }
            }
        }
    }
}

tasks.register("testCoverage") {
    dependsOn(
        ":api:jacocoTestCoverageVerification",
        ":common:jacocoTestCoverageVerification",
        ":sql:jacocoTestCoverageVerification"
    )
}

val distOut = layout.projectDirectory.dir("out")
val distVersion = version.toString()

val collectJars = tasks.register<Copy>("collectJars") {
    group = "build"
    description = "Copies plugin and library jars into out/"
    dependsOn(
        ":paper:shadowJar",
        ":velocity:shadowJar",
        ":bungee:shadowJar",
        ":bench:shadowJar",
        ":api:jar",
        ":common:jar",
        ":sql:jar"
    )
    into(distOut)
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    from(layout.projectDirectory.file("paper/build/libs/aeperm-paper-$distVersion.jar"))
    from(layout.projectDirectory.file("velocity/build/libs/aeperm-velocity-$distVersion.jar"))
    from(layout.projectDirectory.file("bungee/build/libs/aeperm-bungee-$distVersion.jar"))
    from(layout.projectDirectory.file("bench/build/libs/aeperm-bench-$distVersion.jar"))
    from(layout.projectDirectory.file("api/build/libs/aeperm-api-$distVersion.jar"))
    from(layout.projectDirectory.file("common/build/libs/aeperm-common-$distVersion.jar"))
    from(layout.projectDirectory.file("sql/build/libs/aelion-sql-$distVersion.jar"))
}

tasks.named("build") {
    finalizedBy(collectJars)
}

tasks.named<Delete>("clean") {
    delete(distOut)
}
