package io.github.kyay10.highkt.fir

import arrow.core.raise.merge
import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.SessionAndScopeSessionHolder
import org.jetbrains.kotlin.fir.SessionHolder
import org.jetbrains.kotlin.fir.declarations.FirProperty
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.extensions.FirExpressionResolutionExtension
import org.jetbrains.kotlin.fir.references.resolved
import org.jetbrains.kotlin.fir.references.toResolvedCallableSymbol
import org.jetbrains.kotlin.fir.resolve.calls.ImplicitExtensionReceiverValue
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol
import org.jetbrains.kotlin.fir.types.resolvedType
import org.jetbrains.kotlin.fir.visitors.FirDefaultVisitorVoid

class KindReturnTypeRefiner(session: FirSession) : FirExpressionResolutionExtension(session), SessionHolder {
  @OptIn(SymbolInternals::class)
  override fun addNewImplicitReceivers(
    functionCall: FirFunctionCall,
    sessionHolder: SessionAndScopeSessionHolder,
    containingCallableSymbol: FirCallableSymbol<*>
  ): List<ImplicitExtensionReceiverValue> {
    if (functionCall.calleeReference.resolved?.toResolvedCallableSymbol()?.callableId != EXPAND_TO_ID) {
      val type = functionCall.resolvedType
      val shouldCanonicalize = !functionCall.isPropertyInitializer(containingCallableSymbol)
      functionCall.replaceConeTypeOrNull(
        if (shouldCanonicalize) type.applyKAndCanonicalize() else type.applyKEverywhere() ?: type
      )
    }
    return emptyList()
  }
}

@OptIn(SymbolInternals::class)
private fun FirFunctionCall.isPropertyInitializer(containingCallableSymbol: FirCallableSymbol<*>): Boolean = merge {
  containingCallableSymbol.fir.accept(object : FirDefaultVisitorVoid() {
    override fun visitElement(element: FirElement) {
      if (element is FirProperty && element.initializer === this@isPropertyInitializer) raise(true)
      element.acceptChildren(this)
    }
  })
  false
}
