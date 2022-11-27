plugins {
    id("com.android.application")
    id("kotlin-android")
    id("kotlin-kapt")
    id("kotlinx-serialization")
}

repositories {
    google()
    mavenCentral()
}

android {
    compileSdk = 33

    defaultConfig {
        applicationId = "moxy.sample"
        minSdk = 23
        targetSdk = 33
        versionCode = 1
        versionName = "1.0"

        javaCompileOptions {
            annotationProcessorOptions {
                arguments.putAll(mapOf(
                    "disableEmptyStrategyCheck" to "false",
                    "enableEmptyStrategyHelper" to "true",
                    "defaultMoxyStrategy" to "moxy.viewstate.strategy.AddToEndSingleStrategy",
                    "moxyEnableIsolatingProcessing" to "true"
                ))
            }
        }
    }

    signingConfigs {
        create("release") {
            keyAlias = "Moxy"
            keyPassword = "MoxyRelease"
            storePassword = "MoxyRelease"
            storeFile = file("DemoReleaseKeystore")
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android.txt"), "proguard-rules.txt")
        }
    }

    buildFeatures {
        viewBinding = true
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true

        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    packagingOptions {
        exclude("META-INF/*.kotlin_module")
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions {
        freeCompilerArgs = listOf("-Xjvm-default=all")
        jvmTarget = JavaVersion.VERSION_1_8.toString()
    }
}

kapt {
    correctErrorTypes = true
}

dependencies {

    implementation("javax.inject:javax.inject:1")
    implementation("androidx.fragment:fragment:1.5.4")
    implementation ("com.google.guava:listenablefuture:9999.0-empty-to-avoid-conflict-with-guava")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:1.2.0")
    implementation(project(":dagger"))
    kapt(project(":dagger-compiler"))

}
