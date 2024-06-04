plugins {
    id("java")
    id("application")
    id("com.github.johnrengelman.shadow") version "7.1.2"
}

group = "me.iyh"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.google.firebase:firebase-admin:9.2.0")
    implementation("org.slf4j:slf4j-nop:2.0.7")
    testImplementation(platform("org.junit:junit-bom:5.9.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks.test {
    useJUnitPlatform()
}

application {
    mainClass.set("me.iyh.Main")
}
