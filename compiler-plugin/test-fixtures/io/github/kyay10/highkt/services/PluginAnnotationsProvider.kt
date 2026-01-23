package io.github.kyay10.highkt.services

import java.io.File
import org.jetbrains.kotlin.cli.jvm.config.addJvmClasspathRoots
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.test.model.TestModule
import org.jetbrains.kotlin.test.services.EnvironmentConfigurator
import org.jetbrains.kotlin.test.services.RuntimeClasspathProvider
import org.jetbrains.kotlin.test.services.TestServices

class PluginAnnotationsProvider(testServices: TestServices) : EnvironmentConfigurator(testServices) {
  companion object {
    val annotationsRuntimeClasspath =
      System.getProperty("annotationsRuntime.classpath")?.split(File.pathSeparator)?.map(::File)
        ?: error("Unable to get a valid classpath from 'annotationsRuntime.classpath' property")
  }

  override fun configureCompilerConfiguration(configuration: CompilerConfiguration, module: TestModule) {
    configuration.addJvmClasspathRoots(annotationsRuntimeClasspath)
  }
}

class PluginRuntimeAnnotationsProvider(testServices: TestServices) : RuntimeClasspathProvider(testServices) {
  override fun runtimeClassPaths(module: TestModule) = PluginAnnotationsProvider.annotationsRuntimeClasspath
}
