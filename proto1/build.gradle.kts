plugins {
    kotlin("jvm") version "2.0.21"
    application
}

group = "com.hkmixedkeyboard"
version = "0.1.0-proto1"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.0")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.11.0")
}

application {
    mainClass.set("com.hkmixedkeyboard.CommitHarnessKt")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "failed", "skipped")
        showStandardStreams = true
    }
}

kotlin {
    jvmToolchain(21)
}
