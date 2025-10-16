plugins {
  kotlin("jvm")
  id("com.github.gmazzo.buildconfig")
  id("java-gradle-plugin")
  id("com.gradle.plugin-publish")
  id("module.publication")
}

sourceSets {
  main {
    java.setSrcDirs(listOf("src"))
    resources.setSrcDirs(listOf("resources"))
  }
  test {
    java.setSrcDirs(listOf("test"))
    resources.setSrcDirs(listOf("testResources"))
  }
}

dependencies {
  implementation(kotlin("gradle-plugin-api"))

  testImplementation(kotlin("test-junit5"))
}

buildConfig {
  packageName(project.group.toString())

  buildConfigField("String", "KOTLIN_PLUGIN_ID", "\"${rootProject.group}\"")

  val pluginProject = project(":compiler-plugin")
  buildConfigField("String", "KOTLIN_PLUGIN_GROUP", "\"${pluginProject.group}\"")
  buildConfigField("String", "KOTLIN_PLUGIN_NAME", "\"${pluginProject.name}\"")
  buildConfigField("String", "KOTLIN_PLUGIN_VERSION", "\"${pluginProject.version}\"")

  val annotationsProject = project(":plugin-annotations")
  buildConfigField(
    type = "String",
    name = "ANNOTATIONS_LIBRARY_COORDINATES",
    expression = "\"${annotationsProject.group}:${annotationsProject.name}:${annotationsProject.version}\""
  )
}

gradlePlugin {
  website = "https://github.com/kyay10/highkt"
  vcsUrl = "https://github.com/kyay10/highkt"
  plugins {
    create("HighKtPlugin") {
      id = rootProject.group.toString()
      displayName = "HighKT"
      description =
        "A Kotlin Compiler Plugin enabling the usage of Higher-Kinded Types without resorting to inelegant encodings"
      tags = listOf("kotlin-compiler-plugin")
      implementationClass = "io.github.kyay10.highkt.HighKTGradlePlugin"
    }
  }
}
