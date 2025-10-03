package io.github.kyay10.highkt.fir

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.SessionAndScopeSessionHolder
import org.jetbrains.kotlin.fir.SessionHolder
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.extensions.FirExpressionResolutionExtension
import org.jetbrains.kotlin.fir.resolve.calls.ImplicitExtensionReceiverValue
import org.jetbrains.kotlin.fir.resolve.fullyExpandedType
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.ConeKotlinTypeConflictingProjection
import org.jetbrains.kotlin.fir.types.ConeKotlinTypeProjectionIn
import org.jetbrains.kotlin.fir.types.ConeKotlinTypeProjectionOut
import org.jetbrains.kotlin.fir.types.ConeStarProjection
import org.jetbrains.kotlin.fir.types.ConeTypeProjection
import org.jetbrains.kotlin.fir.types.classId
import org.jetbrains.kotlin.fir.types.isStarProjection
import org.jetbrains.kotlin.fir.types.replaceType
import org.jetbrains.kotlin.fir.types.resolvedType
import org.jetbrains.kotlin.fir.types.type
import org.jetbrains.kotlin.fir.types.withArguments
import org.jetbrains.kotlin.types.Variance

class KindReturnTypeRefiner(session: FirSession) : FirExpressionResolutionExtension(session), SessionHolder {
  override fun addNewImplicitReceivers(
    functionCall: FirFunctionCall,
    sessionHolder: SessionAndScopeSessionHolder,
    containingCallableSymbol: FirCallableSymbol<*>
  ): List<ImplicitExtensionReceiverValue> {
    functionCall.replaceConeTypeOrNull(functionCall.resolvedType.applyKEverywhere() ?: functionCall.resolvedType)
    return emptyList()
  }
}

context(c: SessionHolder)
private fun ConeKotlinType.applyKOnTheOutside(): ConeKotlinType? {
  var realType: ConeKotlinType = this
  val appliedTypes = buildList {
    while (realType.fullyExpandedType().classId in K_IDS) {
      realType = realType.fullyExpandedType()
      add(0, realType.typeArguments[1].withVariance(K_VARIANCES[realType.classId]!!))
      realType = realType.typeArguments[0].type ?: return null // Caused by malformed K type
    }
    // Now realType is not a K
  }.toTypedArray()
  if (appliedTypes.isEmpty()) return null // implies this was not a K at all
  if (realType.typeArguments.size != appliedTypes.size) return null
  if (realType.typeArguments.any { !it.isStarProjection }) return null // Could be relaxed
  return realType.withArguments(appliedTypes)
}

private fun ConeKotlinType.withVariance(variance: Variance): ConeTypeProjection =
  when (variance) {
    Variance.INVARIANT -> this
    Variance.IN_VARIANCE -> ConeKotlinTypeProjectionIn(this)
    Variance.OUT_VARIANCE -> ConeKotlinTypeProjectionOut(this)
  }

private fun ConeTypeProjection.withVariance(variance: Variance): ConeTypeProjection =
  when (this) {
    is ConeKotlinTypeConflictingProjection, is ConeStarProjection -> this
    is ConeKotlinType -> withVariance(variance)
    is ConeKotlinTypeProjectionIn -> if (variance.allowsInPosition) this else ConeKotlinTypeConflictingProjection(type)
    is ConeKotlinTypeProjectionOut -> if (variance.allowsOutPosition) this else ConeKotlinTypeConflictingProjection(type)
  }

context(_: SessionHolder)
fun ConeKotlinType.applyKEverywhere(): ConeKotlinType? {
  if (typeArguments.isEmpty()) return null
  val appliedOutside = applyKOnTheOutside()
  val currentType = appliedOutside ?: this
  var replacedAny = appliedOutside != null
  return currentType.withArguments { arg ->
    arg.type?.applyKEverywhere()?.let {
      replacedAny = true
      arg.replaceType(it)
    } ?: arg
  }.takeIf { replacedAny }
}
