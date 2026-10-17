import java.net.Socket
import java.time.Duration

plugins {
    id("java-library")
    id("xyz.jpenilla.run-paper") version "3.1.0"
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.2.build.+")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

tasks {
    runServer {
        // Configure the Minecraft version for our task.
        // This is the only required configuration besides applying the plugin.
        // Your plugin's jar (or shadowJar if present) will be used automatically.
        minecraftVersion("26.2")
        jvmArgs("-Xms2G", "-Xmx2G")

        val sidecarImage = providers.gradleProperty("agonesSidecarImage")
            .orElse("us-docker.pkg.dev/agones-images/release/agones-sdk:1.50.0")
            .get()
        val sidecarName = "agonessync-runserver-sidecar"
        var sidecar: Process? = null

        doFirst {
            check(ProcessBuilder("docker", "info").start().waitFor() == 0) {
                "Docker is required for runServer; start Docker and try again."
            }

            ProcessBuilder(
                "docker", "run", "--rm", "--name", sidecarName,
                "-p", "9358:9358", sidecarImage, "--local"
            ).inheritIO().start().also { sidecar = it }

            try {
                repeat(30) {
                    if (sidecar?.isAlive != true) {
                        error("Agones sidecar exited before becoming ready.")
                    }
                    try {
                        Socket("127.0.0.1", 9358).use { return@doFirst }
                    } catch (_: Exception) {
                        Thread.sleep(Duration.ofSeconds(1).toMillis())
                    }
                }
                error("Timed out waiting for the Agones sidecar on port 9358.")
            } catch (failure: Throwable) {
                sidecar?.destroyForcibly()
                ProcessBuilder("docker", "rm", "-f", sidecarName).start().waitFor()
                throw failure
            }
        }

        doLast {
            sidecar?.destroy()
            sidecar?.waitFor()
            ProcessBuilder("docker", "rm", "-f", sidecarName).start().waitFor()
        }
    }

    processResources {
        val props = mapOf("version" to version, "description" to project.description)
        filesMatching("plugin.yml") {
            expand(props)
        }
    }
}
