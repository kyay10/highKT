package io.github.kyay10.highkt

import io.github.kyay10.highkt.fir.FixAllAssignmentAlterer
import io.github.kyay10.highkt.fir.KindReturnTypeRefinementExtension
import io.github.kyay10.highkt.fir.KindReturnTypeRefinementWorkaround
import org.jetbrains.kotlin.fir.extensions.FirExtensionApiInternals
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar

class HighKTRegistrar : FirExtensionRegistrar() {
  @OptIn(FirExtensionApiInternals::class)
  override fun ExtensionRegistrarContext.configurePlugin() {
    +::KindReturnTypeRefinementExtension
    +::FixAllAssignmentAlterer
    +::KindReturnTypeRefinementWorkaround
  }
}