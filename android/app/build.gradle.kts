import java.util.Properties
import org.gradle.api.GradleException

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.kotlinKapt)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.hilt)
    jacoco
}

if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
    apply(plugin = "com.google.firebase.crashlytics")
}

val localProps = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use { stream ->
            load(stream)
        }
    }
}
val apiBaseUrl = (project.findProperty("API_BASE_URL") as String?)
    ?.takeIf { it.isNotBlank() }
    ?: (localProps.getProperty("API_BASE_URL")?.takeIf { it.isNotBlank() })
    ?: "https://api.cotrip.site/"
val googleServerClientId = (project.findProperty("GOOGLE_SERVER_CLIENT_ID") as String?)
    ?.takeIf { it.isNotBlank() }
    ?: (localProps.getProperty("GOOGLE_SERVER_CLIENT_ID")?.takeIf { it.isNotBlank() })
    ?: (System.getenv("GOOGLE_SERVER_CLIENT_ID")?.takeIf { it.isNotBlank() })
    ?: ""
val requireGoogleClientId = (System.getenv("REQUIRE_GOOGLE_SERVER_CLIENT_ID") ?: "false")
    .equals("true", ignoreCase = true)

if (requireGoogleClientId && googleServerClientId.isBlank()) {
    throw GradleException(
        "GOOGLE_SERVER_CLIENT_ID is required for this build. " +
            "Provide it via GitHub Actions secret/environment or local.properties."
    )
}

android {
    namespace = "nvk.cotrip"
    compileSdk = 34

    defaultConfig {
        applicationId = "nvk.cotrip"
        minSdk = 26
        targetSdk = 34
        versionCode = 4
        versionName = "1.0.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")
        buildConfigField("String", "GOOGLE_SERVER_CLIENT_ID", "\"$googleServerClientId\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.bundles.compose)
    implementation(libs.androidx.compose.material)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)

    implementation(libs.dagger.hilt.android)
    kapt(libs.dagger.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)
    kapt(libs.androidx.hilt.compiler)

    implementation(libs.retrofit)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.security.crypto)
    implementation(libs.coil.compose)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    kapt(libs.room.compiler)

    implementation(libs.androidx.work.runtime.ktx)

    implementation(libs.play.services.auth)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging.ktx)
    implementation(libs.firebase.crashlytics.ktx)
    implementation(libs.kotlinx.coroutines.play.services)

    testImplementation(libs.junit)
    testImplementation("androidx.test:core:1.5.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("org.robolectric:robolectric:4.11.1")
    testImplementation("io.mockk:mockk:1.13.10")
    testImplementation(libs.androidx.compose.ui.test.junit4)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

kapt {
    correctErrorTypes = true
}

jacoco {
    toolVersion = "0.8.11"
}

tasks.withType<Test>().configureEach {
    extensions.configure(JacocoTaskExtension::class.java) {
        isIncludeNoLocationClasses = true
        excludes = listOf("jdk.internal.*")
    }
}

// Avoid running release unit tests in CI; Compose UI tests fail there (Robolectric/RoboMonitoringInstrumentation).
// Coverage and qualityCheck use testDebugUnitTest only.
afterEvaluate {
    tasks.findByName("testReleaseUnitTest")?.let { it.enabled = false }
}

val jacocoExcludes = listOf(
    "**/R.class",
    "**/R$*.class",
    "**/BuildConfig.*",
    "**/Manifest*.*",
    "**/*Test*.*",
    "**/*$*",
    "**/*ScreenKt*",
    "**/*_Factory*",
    "**/*_MembersInjector*",
    "**/*_HiltModules*",
    "**/*Hilt*.*",
    "**/Dagger*.*",
    "**/*_Impl*.*",
    "**/*\$Companion*",
    "**/*\$serializer*",
    "**/MainActivity*",
    "**/CoTripApp*",
    "**/notifications/SystemNotificationManager*",
    "**/di/**",
    "nvk/cotrip/data/network/dto/**",
    "nvk/cotrip/ui/theme/**",
)

val coverageClassDirectories = files(
    fileTree(layout.buildDirectory.dir("tmp/kotlin-classes/debug")) {
        include("nvk/cotrip/**")
        exclude(jacocoExcludes)
    },
    fileTree(layout.buildDirectory.dir("intermediates/javac/debug/classes")) {
        include("nvk/cotrip/**")
        exclude(jacocoExcludes)
    },
)

tasks.register<JacocoReport>("jacocoDebugUnitTestReport") {
    dependsOn("testDebugUnitTest")

    classDirectories.setFrom(coverageClassDirectories)
    sourceDirectories.setFrom(files("src/main/java", "src/main/kotlin"))
    executionData.setFrom(
        fileTree(layout.buildDirectory) {
            include(
                "jacoco/testDebugUnitTest.exec",
                "outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTest.exec",
            )
        }
    )

    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }
}

tasks.register<JacocoCoverageVerification>("jacocoDebugCoverageVerification") {
    dependsOn("testDebugUnitTest")

    classDirectories.setFrom(coverageClassDirectories)
    sourceDirectories.setFrom(files("src/main/java", "src/main/kotlin"))
    executionData.setFrom(
        fileTree(layout.buildDirectory) {
            include(
                "jacoco/testDebugUnitTest.exec",
                "outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTest.exec",
            )
        }
    )

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
    description = "Runs Android JVM tests and coverage verification."
    dependsOn("testDebugUnitTest", "jacocoDebugUnitTestReport", "jacocoDebugCoverageVerification")
}
