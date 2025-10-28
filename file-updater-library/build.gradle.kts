plugins {
    alias(libs.plugins.android.library)
    id("maven-publish")
}

android {
    namespace = "tw.com.innolux.file_updater_lib"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        minSdk = 30

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    publishing {
        singleVariant("release")
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {

                // 告訴這個發佈，要從 "release" 元件獲取檔案
                from(components.getByName("release"))

                // GroupId：必須是 com.github.{您的GitHub使用者名稱}
                groupId = "com.github.skywing61"

                // ArtifactId：您的套件名稱
                artifactId = "file-updater-library"

                // Version：JitPack 會自動抓取
                version = project.version.toString()
            }
        }
    }
}