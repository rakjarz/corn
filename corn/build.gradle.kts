import java.util.Properties

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("maven-publish")
}

android {
    namespace = "net.rakjarz.corn"
    compileSdk = 33

    defaultConfig {
        minSdk = 21

        consumerProguardFiles("consumer-rules.pro")
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
        jvmTarget = JavaVersion.VERSION_17.toString()
    }
}

dependencies {
    implementation("io.reactivex.rxjava3:rxjava:3.1.5")

    implementation("androidx.core:core-ktx:1.10.1")
    testImplementation("junit:junit:4.13.2")
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("maven") {
                pom {
                    name.set("Corn")
                    description.set("Kotlin logging file for android")
                    url.set("https://github.com/rakjarz/corn")

                    developers {
                        developer {
                            id.set("vuptt")
                            name.set("Vu Phan")
                            email.set("ptvu.it@gmail.com")
                        }
                    }

                    scm {
                        connection.set("scm:git:git://github.com/rakjarz/corn.git")
                        developerConnection.set("scm:git:git://github.com/rakjarz/corn.git")
                        url.set("https://github.com/rakjarz/corn")
                    }
                }

                groupId = "net.rakjarz.corn"
                artifactId = "corn"
                version = computeVersionName()

                from(components["release"])
            }
        }
    }
}

fun computeVersionName(): String {
    val runTasks = gradle.startParameter.taskNames
    val versionPropsFile = file("version.properties")

    if (versionPropsFile.canRead()) {
        val versionProps = Properties()
        versionProps.load(versionPropsFile.reader())
        var value = 0

        if ("internalRelease" in runTasks || "iR" in runTasks) {
            value = 1
        }

        val versionMajor = 1
        val versionMinor = 0
        val versionPatch = versionProps.getProperty("VERSION_PATCH").toInt() + value
        val versionBuild = versionProps.getProperty("VERSION_BUILD").toInt() + 1

        versionProps.setProperty("VERSION_PATCH", versionPatch.toString())
        versionProps.setProperty("VERSION_BUILD", versionBuild.toString())
        versionProps.setProperty("version", "$versionMajor.$versionMinor.$versionPatch")
        versionProps.store(versionPropsFile.writer(), null)
        return "$versionMajor.$versionMinor.$versionPatch"
    } else {
        throw GradleException("Could not read version.properties!")
    }
}
