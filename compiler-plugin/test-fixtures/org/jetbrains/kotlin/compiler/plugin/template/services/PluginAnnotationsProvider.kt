package org.jetbrains.kotlin.compiler.plugin.template.services

import org.jetbrains.kotlin.cli.jvm.config.addJvmClasspathRoot
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.test.model.TestModule
import org.jetbrains.kotlin.test.services.EnvironmentConfigurator
import org.jetbrains.kotlin.test.services.RuntimeClasspathProvider
import org.jetbrains.kotlin.test.services.TestServices
import org.jetbrains.kotlin.test.services.standardLibrariesPathProvider
import java.io.File

class PluginAnnotationsProvider(testServices: TestServices) : EnvironmentConfigurator(testServices) {
    companion object {
        val annotationsRuntimeClasspath =
            System.getProperty("annotationsRuntime.classpath")?.split(File.pathSeparator)?.map(::File)
                ?: error("Unable to get a valid classpath from 'annotationsRuntime.classpath' property")
    }

    override fun configureCompilerConfiguration(configuration: CompilerConfiguration, module: TestModule) {
        for (file in annotationsRuntimeClasspath) {
            configuration.addJvmClasspathRoot(file)
        }
    }
}


class PluginRuntimeAnnotationsProvider(testServices: TestServices) : RuntimeClasspathProvider(testServices) {
    override fun runtimeClassPaths(module: TestModule): List<File> =
        listOf(
            testServices.standardLibrariesPathProvider.runtimeJarForTestsWithJdk8(),
            testServices.standardLibrariesPathProvider.runtimeJarForTests().also(::println)
        ) + PluginAnnotationsProvider.annotationsRuntimeClasspath
}