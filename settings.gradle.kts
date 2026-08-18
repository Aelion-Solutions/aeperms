pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
    }
}

plugins {
    id("io.ebean") version "15.8.0" apply false
}

rootProject.name = "aeperm"

include("api", "common", "paper", "velocity", "bungee")
