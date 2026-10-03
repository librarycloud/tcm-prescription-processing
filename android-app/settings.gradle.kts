pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // JPush (极光推送) SDK repository
        maven { url = uri("https://dl.bintray.com/cps/maven") }
        maven { url = uri("https://jcenter.bintray.com") }
    }
}

rootProject.name = "TCMAdmin"
include(":app")
include(":ppocr-sdk")
