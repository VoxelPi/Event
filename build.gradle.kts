plugins {
    alias(libs.plugins.kotlin)
    alias(libs.plugins.dokka)
    alias(libs.plugins.ktlint)
    `java-library`
    `maven-publish`
    signing
}

group = "net.voxelpi.event"
version = "0.3.1-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    compileOnly(kotlin("stdlib"))
    compileOnly(kotlin("reflect"))

    // Tests
    testImplementation(kotlin("stdlib"))
    testImplementation(kotlin("reflect"))
    testImplementation(kotlin("test"))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
}

tasks {
    dokkaHtml.configure {
        outputDirectory.set(layout.buildDirectory.dir("docs"))
    }

    test {
        useJUnitPlatform()
    }

    ktlint {
        version.set("1.0.0")
        verbose.set(true)
        outputToConsole.set(true)
        coloredOutput.set(true)
        reporters {
            reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.CHECKSTYLE)
        }
    }
}

kotlin {
    jvmToolchain(17)
}

val javadocJar by tasks.register<Jar>("dokkaJavadocJar") {
    dependsOn(tasks.dokkaJavadoc)
    from(tasks.dokkaJavadoc.flatMap { it.outputDirectory })
    archiveClassifier.set("javadoc")
}

val sourcesJar by tasks.creating(Jar::class) {
    archiveClassifier.set("sources")
    from(sourceSets.getByName("main").allSource)
}

publishing {
    repositories {
        maven {
            name = "VoxelPiRepo"
            val releasesRepoUrl = "https://repo.voxelpi.net/repository/maven-releases/"
            val snapshotsRepoUrl = "https://repo.voxelpi.net/repository/maven-snapshots/"
            url = uri(if (version.toString().endsWith("SNAPSHOT")) snapshotsRepoUrl else releasesRepoUrl)
            credentials {
                username = findProperty("vpr.user") as String? ?: System.getenv("VOXELPI_REPO_USER")
                password = findProperty("vpr.key") as String? ?: System.getenv("VOXELPI_REPO_KEY")
            }
        }
    }

    publications {
        create<MavenPublication>("maven") {
            groupId = project.group.toString()
            artifactId = project.name
            version = project.version.toString()

            from(components["kotlin"])
            artifact(sourcesJar)
            artifact(javadocJar)

            pom {
                name = project.name
                description = project.description
                url = "https://github.com/voxelpi/event"

                licenses {
                    license {
                        name = "The MIT License"
                        url = "https://opensource.org/licenses/MIT"
                    }
                }

                developers {
                    developer {
                        id = "voxelpi"
                        name = "Peter Smek"
                        url = "https://voxelpi.net"
                    }
                }

                scm {
                    connection = "scm:git:https://github.com/voxelpi/event.git"
                    developerConnection = "scm:git:ssh://git@github.com/voxelpi/event.git"
                    url = "https://github.com/voxelpi/event"
                }

                issueManagement {
                    system = "GitHub"
                    url = "https://github.com/voxelpi/event/issues"
                }

                ciManagement {
                    system = "GitHub Actions"
                    url = "https://github.com/voxelpi/event/actions"
                }
            }
        }
    }
}

signing {
    val signingSecretKey = System.getenv("SIGNING_KEY")
    val signingPassword = System.getenv("SIGNING_PASSWORD")
    if (signingSecretKey != null && signingPassword != null) {
        useInMemoryPgpKeys(signingSecretKey, signingPassword)
    } else {
        useGpgCmd()
    }
    sign(publishing.publications)
}
