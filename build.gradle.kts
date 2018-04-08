import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

group = "me.tatarka.value"
version = "0.3"

buildscript {
    var kotlinVersion: String by extra
    kotlinVersion = "1.2.21"

    repositories {
        mavenCentral()
        maven {
            url = uri("https://plugins.gradle.org/m2/")
        }
    }

    dependencies {
        classpath(kotlin("gradle-plugin", kotlinVersion))
        classpath("me.tatarka.gradle:pom:1.0")
    }
}

plugins {
    `java-library`
    id("org.jetbrains.dokka") version "0.9.16-eap-3"
}

apply {
    plugin("kotlin")
    from("publish.gradle")
}

val kotlinVersion: String by extra

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib-jdk8", kotlinVersion))

    testImplementation("junit:junit:4.12")
    testImplementation("com.google.testing.compile:compile-testing:0.13")
}

configure<JavaPluginConvention> {
    sourceCompatibility = JavaVersion.VERSION_1_8
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}
