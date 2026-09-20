import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.StandardOpenOption

plugins {
    alias(libs.plugins.kotlin.jvm)
    application
    alias(libs.plugins.shadow)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set("com.stratum.solver.SolverApplicationKt")
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:domain-solver"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.firebase.admin)

    // CVE remediation floors (see docs/security-scanning.md): align transitive
    // dependency versions with releases carrying fixes for OSV-flagged
    // vulnerabilities. BOM platforms only add constraints, they don't add
    // dependencies; the httpcomponents pins are direct version constraints.
    implementation(platform("io.netty:netty-bom:4.2.17.Final"))
    implementation(platform("com.fasterxml.jackson:jackson-bom:2.18.9"))
    implementation(platform("io.opentelemetry:opentelemetry-bom:1.62.0"))
    constraints {
        implementation("org.apache.httpcomponents.client5:httpclient5:5.6.3")
        implementation("org.apache.httpcomponents.core5:httpcore5:5.4.3")
        implementation("org.apache.httpcomponents.core5:httpcore5-h2:5.4.3")
    }

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlin.test.junit5)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

tasks.named<ShadowJar>("shadowJar") {
    group = "build"
    description = "Fat jar for Cloud Run (OR-Tools natives + Firebase Admin + domain)"
    archiveBaseName.set("solver-service")
    archiveClassifier.set("all")
    archiveVersion.set("")
    // Required so gRPC SPI entries from grpc-core + grpc-grpclb + grpc-util are concatenated.
    // Without this (or when merge fails), only one provider remains and Firestore panics with:
    // "Could not find policy 'pick_first'".
    mergeServiceFiles()
    isZip64 = true
    // Avoid signed-jar verification failures when merging dependencies.
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")

    // Shadow has been observed to drop grpc-core SPI lines when other gRPC jars also register
    // LoadBalancerProvider / NameResolverProvider. Rewrite critical service files after package.
    doLast {
        val jarFile = archiveFile.get().asFile
        val spiEntries =
            mapOf(
                "META-INF/services/io.grpc.LoadBalancerProvider" to
                    """
                    io.grpc.internal.PickFirstLoadBalancerProvider
                    io.grpc.util.SecretRoundRobinLoadBalancerProvider${'$'}Provider
                    io.grpc.util.OutlierDetectionLoadBalancerProvider
                    io.grpc.grpclb.GrpclbLoadBalancerProvider
                    """.trimIndent() + "\n",
                "META-INF/services/io.grpc.NameResolverProvider" to
                    """
                    io.grpc.internal.DnsNameResolverProvider
                    io.grpc.grpclb.SecretGrpclbNameResolverProvider${'$'}Provider
                    """.trimIndent() + "\n",
            )
        // Zip filesystem: update entries without shelling out to `jar` (config-cache friendly).
        // Note: use imported java.nio.* — Project has a `java {}` block so bare `java.nio` fails.
        FileSystems.newFileSystem(URI.create("jar:" + jarFile.toURI()), mapOf("create" to "false")).use { fs ->
            for ((path, content) in spiEntries) {
                val entry = fs.getPath(path)
                Files.createDirectories(entry.parent)
                Files.writeString(
                    entry,
                    content,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE,
                )
            }
        }
        logger.lifecycle("Patched gRPC SPI into ${jarFile.name}")
    }
}

tasks.named("assemble") {
    dependsOn(tasks.named("shadowJar"))
}

// Prefer the fat jar as the application distribution artifact for Docker.
tasks.named<Jar>("jar") {
    enabled = false
}
