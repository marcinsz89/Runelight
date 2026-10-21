import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.moonbench.bifrost"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "io.github.marcinsz89.runelight"
        minSdk = 33
        targetSdk = 36
        versionCode = 17
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Release signing is externalized to a gitignored keystore.properties so no
    // credentials live in version control. Whoever has the keystore + properties
    // (e.g. the maintainer's local checkout) produces a signed release; everyone
    // else — including CI and other contributors — builds an unsigned release
    // and signs separately. Falls back gracefully when the file is absent.
    val keystorePropsFile = rootProject.file("keystore.properties")
    val hasReleaseSigning = keystorePropsFile.exists()
    if (hasReleaseSigning) {
        val keystoreProps = Properties()
        keystorePropsFile.inputStream().use { keystoreProps.load(it) }
        signingConfigs {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.lifecycle.livedata.core)
    implementation(libs.colorpicker)
    // Removing Compose must not downgrade shared libraries already used by this build.
    constraints {
        implementation(libs.androidx.collection)
        implementation(libs.androidx.emoji2)
        implementation(libs.androidx.emoji2.views.helper)
        implementation(libs.androidx.constraintlayout)
        implementation(libs.androidx.annotation.experimental)
        implementation(libs.kotlinx.coroutines.android)
        implementation(libs.kotlinx.coroutines.core)
    }
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

// Contract tests read the actual manifest after Android/library merging.
tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    if (name in setOf("testDebugUnitTest", "testReleaseUnitTest")) {
        val variant = if (name == "testDebugUnitTest") "debug" else "release"
        val taskVariant = variant.replaceFirstChar { it.uppercase() }
        dependsOn("process${taskVariant}Manifest")
        val mergedManifest = layout.buildDirectory.file(
            "intermediates/merged_manifests/$variant/process${taskVariant}Manifest/AndroidManifest.xml")
        inputs.file(mergedManifest).withPropertyName("mergedManifest")
        systemProperty("runelight.mergedManifest", mergedManifest.get().asFile.absolutePath)
        systemProperty("runelight.debugProbes", variant == "debug")
    }
}
