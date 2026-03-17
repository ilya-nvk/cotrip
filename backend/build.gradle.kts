import org.gradle.api.tasks.testing.Test
import org.gradle.testing.jacoco.plugins.JacocoTaskExtension
import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification
import org.gradle.testing.jacoco.tasks.JacocoReport
import java.math.BigDecimal

plugins {
    kotlin("jvm") version "1.9.22"
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.22"
    id("com.github.johnrengelman.shadow") version "8.1.1"
    jacoco
    application
}

group = "nvk.cotrip"
version = "0.1.0"

application {
    mainClass.set("nvk.cotrip.backend.ApplicationKt")
}

repositories {
    mavenCentral()
}

configurations.configureEach {
    exclude(group = "com.google.guava", module = "listenablefuture")
}

val ktorVersion = "2.3.8"
val logbackVersion = "1.4.14"

dependencies {
    implementation("io.ktor:ktor-server-core-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-netty-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation-jvm:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-auth-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-auth-jwt-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-websockets-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-call-logging-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-status-pages-jvm:$ktorVersion")

    implementation("io.ktor:ktor-client-core-jvm:$ktorVersion")
    implementation("io.ktor:ktor-client-cio-jvm:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation-jvm:$ktorVersion")
    implementation("ch.qos.logback:logback-classic:$logbackVersion")
    implementation("com.zaxxer:HikariCP:5.1.0")
    implementation("org.postgresql:postgresql:42.7.1")
    implementation("com.google.firebase:firebase-admin:9.2.0")

    testImplementation(kotlin("test"))
    testImplementation("io.ktor:ktor-server-tests-jvm:$ktorVersion")
    testImplementation("io.ktor:ktor-client-mock-jvm:$ktorVersion")
    testImplementation("io.mockk:mockk:1.13.10")
    testImplementation("org.testcontainers:junit-jupiter:1.19.7")
    testImplementation("org.testcontainers:postgresql:1.19.7")
}

kotlin {
    jvmToolchain(17)
}

tasks.test {
    useJUnitPlatform()
    finalizedBy("jacocoTestReport")
}

jacoco {
    toolVersion = "0.8.11"
}

val mainSourceSet = sourceSets["main"]
val testTask = tasks.named<Test>("test")
val testExecData = testTask.map { test ->
    test.extensions.getByType(JacocoTaskExtension::class.java).destinationFile
}

val jacocoClassDirs = mainSourceSet.output.asFileTree.matching {
    exclude(
        "**/ApplicationKt.class",
        "**/plugins/Logging*.class",
        "**/DatabaseFactory*.class",
        "**/routes/**",
        "**/db/**",
        "**/plugins/**",
        "**/notifications/FirebasePushService*.class",
        "**/ws/CommentEventsPublisher*.class",
        "**/ws/CommentsHub*.class",
    )
}

tasks.named<JacocoReport>("jacocoTestReport") {
    dependsOn(testTask)

    executionData.setFrom(files(testExecData))
    sourceDirectories.setFrom(mainSourceSet.allSource.srcDirs)
    classDirectories.setFrom(jacocoClassDirs)

    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }
}

tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    dependsOn(testTask)
    executionData.setFrom(files(testExecData))
    sourceDirectories.setFrom(mainSourceSet.allSource.srcDirs)
    classDirectories.setFrom(jacocoClassDirs)

    violationRules {
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = BigDecimal("0.50")
            }
            limit {
                counter = "BRANCH"
                value = "COVEREDRATIO"
                minimum = BigDecimal("0.40")
            }
        }
    }
}

tasks.register("qualityCheck") {
    group = "verification"
    description = "Runs backend tests and coverage verification."
    dependsOn("test", "jacocoTestReport", "jacocoTestCoverageVerification")
}

tasks.withType<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>().configureEach {
    mergeServiceFiles()
}
