plugins {
    `java-library`
    id("io.ebean") version "15.8.0"
}

dependencies {
    api(project(":api"))

    implementation("io.ebean:ebean:15.8.0")
    implementation("io.ebean:ebean-core:15.8.0")
    annotationProcessor("io.ebean:querybean-generator:15.8.0")
    implementation("jakarta.persistence:jakarta.persistence-api:3.2.0")
    implementation("com.zaxxer:HikariCP:6.3.0")
    implementation("org.postgresql:postgresql:42.7.7")
    implementation("redis.clients:jedis:5.2.0")
    implementation("com.google.code.gson:gson:2.13.1")
    implementation("org.yaml:snakeyaml:2.4")

    implementation("net.kyori:adventure-api:4.25.0")
    implementation("net.kyori:adventure-text-minimessage:4.25.0")
    implementation("net.kyori:adventure-text-serializer-plain:4.25.0")
    api("com.mojang:brigadier:1.3.10")

    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core:3.27.3")
}

ebean {
    debugLevel = 0
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    classDirectories.setFrom(
        files(classDirectories.files.map {
            fileTree(it) {
                exclude(
                    "**/storage/EbeanStorage*",
                    "**/storage/ebean/**",
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
