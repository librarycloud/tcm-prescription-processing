pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven { url = uri("https://developer.huawei.com/repo/") }
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://developer.huawei.com/repo/") }
        maven { url = uri("https://developer.hihonor.com/repo") }
        // JPush (极光推送) SDK repository
        maven { url = uri("https://dl.bintray.com/cps/maven") }
        maven { url = uri("https://jcenter.bintray.com") }
    }
}

rootProject.name = "TCMAdmin"
include(":app")
include(":ppocr-sdk")
