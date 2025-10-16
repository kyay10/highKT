@file:Suppress("UnstableApiUsage")

pluginManagement {
  includeBuild("convention-plugins")
  repositories {
    mavenCentral()
    gradlePluginPortal()
  }
}

plugins {
  id("org.gradle.toolchains.foojay-resolver-convention") version "0.10.0"
}

dependencyResolutionManagement {
  repositories {
    mavenCentral()
  }
}

rootProject.name = "highKT"

include("compiler-plugin")
include("gradle-plugin")
include("plugin-annotations")
