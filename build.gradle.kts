plugins {
    kotlin("jvm") version "1.8.21"
    kotlin("plugin.serialization") version "1.8.21"
    application
}

group = "org.jetbrains.bsp"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.5.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.1")
    implementation("io.github.rhdunn:xqt-kotlinx-json-rpc:1.0.3")
    implementation("io.arrow-kt:arrow-core:1.2.0-RC")
    implementation("io.arrow-kt:arrow-fx-coroutines:1.2.0-RC")
//    implementation("org.jetbrains.kotlin:kotlin-reflect:1.8.21")
    implementation(kotlin("stdlib"))
//    testImplementation("junit:junit:4.13.2")
}

dependencies {
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(11)
}

application {
    mainClass.set("MainKt")
}