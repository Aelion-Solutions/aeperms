plugins {
    `java-library`
}

dependencies {
    api(project(":api"))
    api(project(":sql")) {
        isTransitive = false
    }

    implementation("org.postgresql:postgresql:42.7.7")
    implementation("org.mariadb.jdbc:mariadb-java-client:3.5.6")
    implementation("redis.clients:jedis:5.2.0")
    implementation("com.google.code.gson:gson:2.13.1")
    implementation("org.yaml:snakeyaml:2.4")

    implementation("net.kyori:adventure-api:4.25.0")
    implementation("net.kyori:adventure-text-minimessage:4.25.0")
    implementation("net.kyori:adventure-text-serializer-plain:4.25.0")
    api("com.mojang:brigadier:1.3.10")

    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core:3.27.7")
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    classDirectories.setFrom(
        files(classDirectories.files.map {
            fileTree(it) {
                exclude(
                    "**/storage/SqlStorage*",
                    "**/sync/RedisSyncBus*",
                    "**/query/**"
                )
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
