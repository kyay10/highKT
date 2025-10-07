package io.github.kyay10.highkt.services

import io.github.kyay10.highkt.HighKTRegistrar
import io.github.kyay10.highkt.ir.RemoveKCastsGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrarAdapter
import org.jetbrains.kotlin.test.model.TestModule
import org.jetbrains.kotlin.test.services.EnvironmentConfigurator
import org.jetbrains.kotlin.test.services.TestServices

class ExtensionRegistrarConfigurator(testServices: TestServices) : EnvironmentConfigurator(testServices) {
  override fun CompilerPluginRegistrar.ExtensionStorage.registerCompilerExtensions(
    module: TestModule,
    configuration: CompilerConfiguration
  ) {
    FirExtensionRegistrarAdapter.registerExtension(HighKTRegistrar())
    IrGenerationExtension.registerExtension(RemoveKCastsGenerationExtension())
  }
}
