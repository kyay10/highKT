package org.jetbrains.kotlin.compiler.plugin.template.fir

import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.extensions.FirExtensionApiInternals
import org.jetbrains.kotlin.fir.extensions.FirFunctionCallRefinementExtension
import org.jetbrains.kotlin.fir.references.builder.buildResolvedNamedReference
import org.jetbrains.kotlin.fir.resolve.calls.candidate.CallInfo
import org.jetbrains.kotlin.fir.resolve.fullyExpandedType
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.isStarProjection
import org.jetbrains.kotlin.fir.types.resolvedType
import org.jetbrains.kotlin.fir.types.withArguments
import org.jetbrains.kotlin.fir.types.withAttributes
import org.jetbrains.kotlin.name.Name

@OptIn(FirExtensionApiInternals::class)
class KindReturnTypeRefinementExtension(session: FirSession) : FirFunctionCallRefinementExtension(session) {
  override fun intercept(
    callInfo: CallInfo,
    symbol: FirNamedFunctionSymbol
  ): CallReturnType? {
    callInfo.explicitReceiver?.let {
      val newType = it.resolvedType.applyKOnce() ?: return@let it
      it.replaceConeTypeOrNull(newType)
    }
    val returnType = symbol.resolvedReturnTypeRef.coneType
    returnType.attributes.kind ?: return null
    return CallReturnType(symbol.resolvedReturnTypeRef) {
      it
    }
  }

  override fun transform(
    call: FirFunctionCall,
    originalSymbol: FirNamedFunctionSymbol
  ): FirFunctionCall {
    val returnType = call.resolvedType.fullyExpandedType(session)
    call.extensionReceiver?.let {
      val newType = it.resolvedType.applyKOnce() ?: return@let it
      it.replaceConeTypeOrNull(newType)
    }
    val newType = returnType.applyKOnce() ?: return call
    call.replaceConeTypeOrNull(newType)
    call.replaceCalleeReference(buildResolvedNamedReference {
      name = originalSymbol.name
      source = originalSymbol.source
      resolvedSymbol = originalSymbol
    })
    return call
  }

  override fun anchorElement(symbol: FirRegularClassSymbol): KtSourceElement {
    TODO("Not yet implemented")
  }

  override fun ownsSymbol(symbol: FirRegularClassSymbol) = false

  override fun restoreSymbol(
    call: FirFunctionCall,
    name: Name
  ) = null
}

private fun ConeKotlinType.applyKOnce(): ConeKotlinType? {
  val kind = attributes.kind ?: return null
  val firstStar = typeArguments.withIndex().indexOfFirst { it.value.isStarProjection }
  if (firstStar == -1) return null
  return withArguments(typeArguments.toMutableList().apply {
    this[firstStar] = kind.coneType
  }.toTypedArray()).withAttributes(attributes.remove(kind))
}