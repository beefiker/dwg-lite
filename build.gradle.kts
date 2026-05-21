import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.gradle.jvm.tasks.Jar

plugins {
    kotlin("jvm") version "2.2.10"
    `java-library`
    `maven-publish`
}

group = "dev.jaeyoung"
version = "0.1.0-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
    withSourcesJar()
}

val javadocJar by tasks.registering(Jar::class) {
    archiveClassifier.set("javadoc")
    from(tasks.named("javadoc"))
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            artifact(javadocJar)
            artifactId = "dwg-lite"
            pom {
                name.set("dwg-lite")
                description.set("Lightweight Kotlin DWG parser for preview-oriented apps")
                url.set("https://github.com/beefiker/dwg-lite")
                licenses {
                    license {
                        name.set("Mozilla Public License Version 2.0")
                        url.set("https://www.mozilla.org/en-US/MPL/2.0/")
                    }
                }
                developers {
                    developer {
                        id.set("beefiker")
                        name.set("Beefiker")
                        email.set("beefiker@gmail.com")
                    }
                }
                scm {
                    connection.set("scm:git:https://github.com/beefiker/dwg-lite.git")
                    developerConnection.set("scm:git:https://github.com/beefiker/dwg-lite.git")
                    url.set("https://github.com/beefiker/dwg-lite")
                }
            }
        }
    }
}
