plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.dokka)
    `maven-publish`
    signing
}

val sdkVersion = "1.4.0" // x-release-please-version
val sdkGroupId = "com.admoai"
val sdkArtifactId = "admoai-android"

android {
    namespace = "com.admoai.sdk"
    compileSdk = 35

    defaultConfig {
        minSdk = 24

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
        
        // Add version info to manifest
        buildConfigField("String", "SDK_VERSION", "\"$sdkVersion\"")
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
    buildFeatures {
        compose = true
        buildConfig = true  // Enable BuildConfig feature for custom fields
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
        freeCompilerArgs += "-opt-in=kotlinx.serialization.InternalSerializationApi" 
    }
    lint {
        disable.add("FlowOperatorInvokedInComposition")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // Kotlin dependencies
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    // Ktor HTTP Client Dependencies
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.client.logging)

    // Jetpack Compose Runtime
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // Testing dependencies
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.mockwebserver)
}

android {
    publishing {
        singleVariant("release") {
            withSourcesJar()
            withJavadocJar()
        }
    }
}

// Journey SDK-driven E2E runner (adhub#2360). A non-JUnit command runner: it drives the real SDK against
// a locally-running decision-engine and asserts SDK-observable Journey business rules. NOT part of
// `:sdk:test` (which stays hermetic). Boot the engine per the plan's recipe, then:
//   ADMOAI_JOURNEY_E2E_BASE_URL=http://127.0.0.1:8080/ ./gradlew :sdk:journeyE2e
// It runs on the unit-test runtime classpath (carries android.jar stubs + MockWebServer + Ktor CIO),
// which is why the runner lives in src/test and can reach the SDK's internal test seams (logSink, etc.).
afterEvaluate {
    val unitTest = tasks.named<Test>("testDebugUnitTest").get()
    tasks.register<JavaExec>("journeyE2e") {
        group = "verification"
        description = "Runs the Journey SDK-driven E2E runner against ADMOAI_JOURNEY_E2E_BASE_URL."
        // Assigning the unit-test classpath (a task-output FileCollection) wires the compile dependencies
        // automatically, so the runner's classes are built without executing the JUnit suite.
        classpath = unitTest.classpath
        mainClass.set("com.admoai.sdk.e2e.JourneyE2eRunnerKt")
        environment("ADMOAI_JOURNEY_E2E_BASE_URL", System.getenv("ADMOAI_JOURNEY_E2E_BASE_URL") ?: "http://127.0.0.1:8080/")
        environment("ADMOAI_JOURNEY_E2E_VERSION", System.getenv("ADMOAI_JOURNEY_E2E_VERSION") ?: "2025-11-01")
    }
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                groupId = sdkGroupId
                artifactId = sdkArtifactId
                version = sdkVersion

                from(components["release"])

                pom {
                    name.set("AdMoai Android SDK")
                    description.set("Android SDK for AdMoai advertising platform with targeting, tracking, and analytics")
                    url.set("https://github.com/admoai/admoai-android")
                    
                    licenses {
                        license {
                            name.set("MIT License")
                            url.set("https://opensource.org/licenses/MIT")
                        }
                    }
                    
                    developers {
                        developer {
                            id.set("admoai")
                            name.set("AdMoai Team")
                            url.set("https://github.com/admoai")
                        }
                    }
                    
                    scm {
                        connection.set("scm:git:git://github.com/admoai/admoai-android.git")
                        developerConnection.set("scm:git:ssh://git@github.com/admoai/admoai-android.git")
                        url.set("https://github.com/admoai/admoai-android")
                    }
                }
            }
        }

        repositories {
            maven {
                name = "GitHubPackages"
                url = uri("https://maven.pkg.github.com/admoai/admoai-android")
                credentials {
                    username = System.getenv("GITHUB_ACTOR") ?: project.findProperty("gpr.user")?.toString() ?: ""
                    password = System.getenv("GITHUB_TOKEN") ?: project.findProperty("gpr.key")?.toString() ?: ""
                }
            }
        }
    }
    
    signing {
        val signingKey: String? = findProperty("signingKey") as String?
        val signingPassword: String? = findProperty("signingPassword") as String?
        if (signingKey != null) {
            useInMemoryPgpKeys(signingKey, signingPassword ?: "")
        } else {
            useGpgCmd()
        }
        sign(publishing.publications)
    }
}

val createMavenCentralBundle by tasks.registering(Zip::class) {
    group = "publishing"
    description = "Creates Maven Central upload bundle"
    
    archiveFileName.set("${sdkArtifactId}-${sdkVersion}-maven-central-bundle.zip")
    destinationDirectory.set(layout.buildDirectory.dir("maven-central"))
    
    dependsOn(tasks.publishToMavenLocal)
    
    from(layout.buildDirectory.dir("repo")) {
        include("**/*")
    }
    
    doFirst {
        val localRepo = File(System.getProperty("user.home"), ".m2/repository")
        val groupPath = sdkGroupId.replace(".", "/")
        val artifactPath = "$groupPath/$sdkArtifactId/$sdkVersion"
        
        copy {
            from(File(localRepo, artifactPath))
            into(layout.buildDirectory.dir("repo/$artifactPath"))
            include("**/*")
        }
    }
}
