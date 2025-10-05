package io.github.kyay10.highkt

import io.github.kyay10.highkt.fir.AddTypeAssertTransformer
import io.github.kyay10.highkt.fir.HighKTCheckers
import io.github.kyay10.highkt.fir.KindReturnTypeRefiner
import org.jetbrains.kotlin.fir.extensions.FirExtensionApiInternals
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar

class HighKTRegistrar : FirExtensionRegistrar() {
  @OptIn(FirExtensionApiInternals::class)
  override fun ExtensionRegistrarContext.configurePlugin() {
    +::AddTypeAssertTransformer
    +::KindReturnTypeRefiner
    +::HighKTCheckers
  }
}