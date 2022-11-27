// Top-level build file where you can add configuration options common to all sub-projects/modules.

buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:7.3.1")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.7.21")
        classpath("org.jetbrains.kotlin:kotlin-serialization:1.7.21")
    }
}

plugins {
    id("com.github.ben-manes.versions") version "0.31.0"
    id("org.jetbrains.dokka") version "1.4.32"
    id("com.vanniktech.maven.publish") version "0.15.1" apply false
}

subprojects {
    repositories {
        google()
        mavenCentral()
        maven {
            setUrl("https://kotlin.bintray.com/kotlinx")
            content {
                includeGroup("org.jetbrains.kotlinx")
            }
        }

        val repo = maven {
            url = rootProject.file("build/localMavenPublish").toURI()
            content {
                includeGroup("com.github.moxy-community")
            }
        }
        remove(repo)
        addFirst(repo)
    }
}

subprojects {
    apply(plugin = "checkstyle")

    tasks.register<Checkstyle>("checkstyle") {
        description = "Runs Checkstyle inspection"
        group = "moxy"
        configFile = rootProject.file("checkstyle.xml")
        ignoreFailures = false
        isShowViolations = true
        classpath = files()
        exclude("**/*.kt")
        source("src/main/java")
    }

    afterEvaluate {
        tasks.named("check").configure { dependsOn("checkstyle") }
    }
}

fun isNonStable(version: String): Boolean {
    val stableKeyword = listOf("RELEASE", "FINAL", "GA").any { version.toUpperCase().contains(it) }
    val regex = "^[0-9,.v-]+(-r)?$".toRegex()
    val isStable = stableKeyword || regex.matches(version)
    return isStable.not()
}

tasks.named<com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask>("dependencyUpdates").configure {
    rejectVersionIf { isNonStable(candidate.version) }
}