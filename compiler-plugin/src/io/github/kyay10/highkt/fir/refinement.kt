package io.github.kyay10.highkt.fir

import org.jetbrains.kotlin.fir.SessionHolder
import org.jetbrains.kotlin.fir.declarations.hasAnnotation
import org.jetbrains.kotlin.fir.resolve.createParametersSubstitutor
import org.jetbrains.kotlin.fir.resolve.fullyExpandedType
import org.jetbrains.kotlin.fir.resolve.toRegularClassSymbol
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.ConeKotlinTypeConflictingProjection
import org.jetbrains.kotlin.fir.types.ConeKotlinTypeProjectionIn
import org.jetbrains.kotlin.fir.types.ConeKotlinTypeProjectionOut
import org.jetbrains.kotlin.fir.types.ConeStarProjection
import org.jetbrains.kotlin.fir.types.ConeTypeProjection
import org.jetbrains.kotlin.fir.types.classId
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.fir.types.isStarProjection
import org.jetbrains.kotlin.fir.types.replaceType
import org.jetbrains.kotlin.fir.types.type
import org.jetbrains.kotlin.fir.types.withArguments
import org.jetbrains.kotlin.types.Variance


context(c: SessionHolder)
private fun ConeKotlinType.applyKOnTheOutside(): ConeKotlinType? {
  var realType: ConeKotlinType = fullyExpandedType()
  val appliedTypes = buildList {
    while (realType.classId in K_IDS) {
      add(0, realType.typeArguments[1].withVariance(K_VARIANCES[realType.classId]!!))
      realType = realType.typeArguments[0].type?.fullyExpandedType() ?: return null // Caused by malformed K type
    }
    // Now realType is not a K
  }.toTypedArray()
  if (appliedTypes.isEmpty()) return null // implies this was not a K at all
  if (realType.typeArguments.size != appliedTypes.size) return null
  if (realType.typeArguments.any { !it.isStarProjection }) return null // Could be relaxed
  realType.toRegularClassSymbol(c.session)?.applyTypeFunctions(appliedTypes)
    ?.let { return it.applyKOnTheOutside() ?: it }
  return realType.withArguments(appliedTypes)
}

@OptIn(SymbolInternals::class)
context(c: SessionHolder)
private fun FirRegularClassSymbol.applyTypeFunctions(
  appliedTypes: Array<out ConeTypeProjection>
): ConeKotlinType? {
  if (!hasAnnotation(TYPE_FUNCTION_CLASS_ID, c.session)) return null
  if (classId == IDENTITY_CLASS_ID) return appliedTypes.single().type
  val superType = fir.superTypeRefs.singleOrNull()?.coneType ?: return null
  val substitutor = createParametersSubstitutor(c.session, ownTypeParameterSymbols.zip(appliedTypes).toMap())
  val substituted = substitutor.substituteOrNull(superType) ?: return null
  return substituted.toRegularClassSymbol(c.session)?.applyTypeFunctions(substituted.typeArguments) ?: substituted
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
  if (appliedOutside?.typeArguments?.isEmpty() == true) return appliedOutside
  val currentType = appliedOutside ?: this
  var replacedAny = appliedOutside != null
  val withReplacedArguments = currentType.withArguments { arg ->
    arg.type?.applyKEverywhere()?.let {
      replacedAny = true
      arg.replaceType(it)
    } ?: arg
  }
  return if (replacedAny) withReplacedArguments.applyKEverywhere() ?: withReplacedArguments else null
}