package io.github.kyay10.highkt.fir

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.SessionAndScopeSessionHolder
import org.jetbrains.kotlin.fir.SessionHolder
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.extensions.FirExpressionResolutionExtension
import org.jetbrains.kotlin.fir.references.resolved
import org.jetbrains.kotlin.fir.references.toResolvedCallableSymbol
import org.jetbrains.kotlin.fir.resolve.calls.ImplicitExtensionReceiverValue
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol
import org.jetbrains.kotlin.fir.types.ConeTypeIntersector
import org.jetbrains.kotlin.fir.types.resolvedType
import org.jetbrains.kotlin.fir.types.typeContext

class KindReturnTypeRefiner(session: FirSession) : FirExpressionResolutionExtension(session), SessionHolder {
  override fun addNewImplicitReceivers(
    functionCall: FirFunctionCall,
    sessionHolder: SessionAndScopeSessionHolder,
    containingCallableSymbol: FirCallableSymbol<*>
  ): List<ImplicitExtensionReceiverValue> {
    if (functionCall.calleeReference.resolved?.toResolvedCallableSymbol()?.callableId != EXPAND_TO_ID) {
      functionCall.replaceConeTypeOrNull(functionCall.resolvedType.applyKEverywhere()?.let {
        ConeTypeIntersector.intersectTypes(session.typeContext, listOf(it, functionCall.resolvedType))
      } ?: functionCall.resolvedType)
    }
    return emptyList()
  }
}