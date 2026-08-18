plugins {
    `java-library`
    id("com.gradleup.shadow")
}

dependencies {
    implementation("com.zaxxer:HikariCP:6.3.0")
    implementation("com.github.ben-manes.caffeine:caffeine:3.2.4")

    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core:3.27.7")
}

tasks.jar {
    archiveClassifier.set("thin")
}

tasks.shadowJar {
    archiveClassifier.set("")
    archiveFileName.set("aelion-sql-${project.version}.jar")
    relocate("com.zaxxer.hikari", "sh.aelion.libs.hikari")
    relocate("com.github.benmanes.caffeine", "sh.aelion.libs.caffeine")
    mergeServiceFiles()
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
}

tasks.named("assemble") {
    dependsOn(tasks.shadowJar)
}

tasks.named("build") {
    dependsOn(tasks.shadowJar)
}

listOf("apiElements", "runtimeElements").forEach { configurationName ->
    configurations.named(configurationName) {
        outgoing.artifacts.clear()
        outgoing.artifact(tasks.shadowJar)
    }
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    classDirectories.setFrom(
        files(classDirectories.files.map {
            fileTree(it) {
                exclude("**/AelionDb*")
            }
        })
    )
    reports {
        xml.required = true
        html.required = true
    }
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.jacocoTestReport)
    classDirectories.setFrom(tasks.jacocoTestReport.get().classDirectories)
    violationRules {
        rule {
            limit {
                minimum = "0.80".toBigDecimal()
            }
        }
    }
}
