package io.github.kyay10.highkt

import io.github.kyay10.highkt.fir.KindReturnTypeRefiner
import io.github.kyay10.highkt.fir.LeaveUnevaluatedAttributeExtension
import org.jetbrains.kotlin.fir.extensions.FirExtensionApiInternals
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar

class HighKTRegistrar : FirExtensionRegistrar() {
  @OptIn(FirExtensionApiInternals::class)
  override fun ExtensionRegistrarContext.configurePlugin() {
    +::LeaveUnevaluatedAttributeExtension
    +::KindReturnTypeRefiner
  }
}