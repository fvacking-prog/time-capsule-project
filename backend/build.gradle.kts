import java.util.zip.ZipFile
import java.util.zip.ZipEntry

plugins {
    kotlin("jvm") version "2.0.21"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21"
    application
}

group = "com.timecapsule"
version = "1.0.0"

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set("com.timecapsule.server.ApplicationKt")
}

val ktorVersion = "3.0.1"
val exposedVersion = "0.56.0"
val h2Version = "2.3.232"
val logbackVersion = "1.5.12"

dependencies {
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    implementation("io.ktor:ktor-server-cors:$ktorVersion")
    implementation("io.ktor:ktor-server-status-pages:$ktorVersion")

    implementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")
    implementation("com.h2database:h2:$h2Version")
    implementation("com.google.api-client:google-api-client:2.7.0")

    implementation("ch.qos.logback:logback-classic:$logbackVersion")

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
    environment("CAPSULE_AES_KEY", "dGVzdC10aW1lLWNhcHN1bGUtYWVzLTI1Ni1rZXktMzI=")
}

val mergeServicesDir = layout.buildDirectory.dir("merged-services")

val prepareMergedServices by tasks.registering {
    inputs.files(configurations.runtimeClasspath)
    outputs.dir(mergeServicesDir)
    doLast {
        val outDir = mergeServicesDir.get().asFile.resolve("META-INF/services")
        outDir.mkdirs()
        val mergedMap = mutableMapOf<String, MutableSet<String>>()
        configurations.runtimeClasspath.get().filter { it.name.endsWith(".jar") }.forEach { jar ->
            try {
                ZipFile(jar).use { zip ->
                    val entries = zip.entries()
                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        if (!entry.isDirectory && entry.name.startsWith("META-INF/services/")) {
                            val serviceName = entry.name.removePrefix("META-INF/services/")
                            val lines = zip.getInputStream(entry).bufferedReader().readLines()
                                .map { line -> line.trim() }
                                .filter { line -> line.isNotEmpty() && !line.startsWith("#") }
                            mergedMap.getOrPut(serviceName) { mutableSetOf() }.addAll(lines)
                        }
                    }
                }
            } catch (_: Exception) {}
        }
        mergedMap.forEach { (serviceName, lines) ->
            val serviceFile = File(outDir, serviceName)
            serviceFile.parentFile.mkdirs()
            serviceFile.writeText(lines.joinToString("\n") + "\n")
        }
    }
}

tasks.register<Jar>("fatJar") {
    dependsOn(prepareMergedServices)
    archiveBaseName.set("timecapsule-server")
    archiveClassifier.set("")
    archiveVersion.set("")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest {
        attributes["Main-Class"] = "com.timecapsule.server.ApplicationKt"
    }
    from(sourceSets.main.get().output)
    from(mergeServicesDir)
    from({
        configurations.runtimeClasspath.get().filter { it.name.endsWith("jar") }.map { jar ->
            zipTree(jar).matching {
                exclude("META-INF/services/**")
                exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
            }
        }
    })
}

