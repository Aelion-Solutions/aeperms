pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
    }
}

rootProject.name = "aeperm"

include("api", "common", "paper", "velocity", "bungee", "sql")
