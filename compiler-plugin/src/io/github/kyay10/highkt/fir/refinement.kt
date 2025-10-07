package io.github.kyay10.highkt.fir

import org.jetbrains.kotlin.fir.SessionHolder
import org.jetbrains.kotlin.fir.declarations.hasAnnotation
import org.jetbrains.kotlin.fir.plugin.createConeType
import org.jetbrains.kotlin.fir.resolve.createParametersSubstitutor
import org.jetbrains.kotlin.fir.resolve.fullyExpandedType
import org.jetbrains.kotlin.fir.resolve.toRegularClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.types.ConeIntersectionType
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.ConeStarProjection
import org.jetbrains.kotlin.fir.types.ConeTypeIntersector
import org.jetbrains.kotlin.fir.types.ConeTypeProjection
import org.jetbrains.kotlin.fir.types.classId
import org.jetbrains.kotlin.fir.types.constructType
import org.jetbrains.kotlin.fir.types.isStarProjection
import org.jetbrains.kotlin.fir.types.replaceType
import org.jetbrains.kotlin.fir.types.type
import org.jetbrains.kotlin.fir.types.typeContext
import org.jetbrains.kotlin.fir.types.withArguments

context(c: SessionHolder)
private fun ConeKotlinType.applyKOnTheOutside(): ConeKotlinType? {
  val (realType, appliedTypes) = deconstructIfKType() ?: return null
  return realType.toRegularClassSymbol(c.session)?.applyTypeFunctions(appliedTypes)
}

context(c: SessionHolder)
private fun ConeKotlinType.deconstructIfKType(): Pair<ConeKotlinType, List<ConeTypeProjection>>? {
  var realType: ConeKotlinType = fullyExpandedType()
  val appliedTypes = buildList {
    while (realType.classId == K_CLASS_ID) {
      add(0, realType.typeArguments[1])
      realType = realType.typeArguments[0].type?.fullyExpandedType() ?: return null // Caused by malformed K type
    }
    // Now realType is not a K
  }
  if (appliedTypes.isEmpty()) return null // implies this was not a K at all
  // Could be relaxed
  require(realType.typeArguments.none { !it.isStarProjection }) { "K can only be applied to star projections; was called with $realType" }
  return realType to appliedTypes
}

context(c: SessionHolder)
private fun ConeKotlinType.deconstructNormalType(): Pair<ConeKotlinType, List<ConeTypeProjection>> {
  val realType = fullyExpandedType()
  return realType to realType.typeArguments.toList()
}

context(c: SessionHolder)
private tailrec fun FirRegularClassSymbol.applyTypeFunctions(
  appliedTypes: List<ConeTypeProjection>, default: ConeKotlinType? = null
): ConeKotlinType? {
  if (ownTypeParameterSymbols.size > appliedTypes.size) return default // partially-applied type
  require(classId != K_CLASS_ID) { "Should not be called on K; was called with arguments $appliedTypes" }
  if (!hasAnnotation(TYPE_FUNCTION_CLASS_ID, c.session)) {
    if (ownTypeParameterSymbols.size != appliedTypes.size) return default
    return constructType(appliedTypes.toTypedArray())
  }
  // TODO what if K<Identity, in/out A>?
  val substituted = if (classId == IDENTITY_CLASS_ID) appliedTypes.first().type else {
    val superType = resolvedSuperTypeRefs.single().coneType
    val substitutor = createParametersSubstitutor(c.session, ownTypeParameterSymbols.zip(appliedTypes).toMap())
    substitutor.substituteOrNull(superType)
  } ?: return default
  val appliedTypes = appliedTypes.drop(ownTypeParameterSymbols.size)
  val newDefault = substituted.createKType(appliedTypes)
  val (realType, newApplied) = substituted.deconstructIfKType() ?: substituted.deconstructNormalType()
  val symbol = realType.toRegularClassSymbol(c.session) ?: return newDefault
  return symbol.applyTypeFunctions(newApplied + appliedTypes, newDefault)
}

// TODO: application order?
context(_: SessionHolder)
fun ConeKotlinType.applyKEverywhere(): ConeKotlinType? {
  if (this is ConeIntersectionType) return mapTypesNotNull { it.applyKEverywhere() }
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
  return withReplacedArguments.takeIf { replacedAny }
}

context(c: SessionHolder)
fun ConeKotlinType.toCanonicalKType(): ConeKotlinType? {
  if (this is ConeIntersectionType) return mapTypesNotNull { it.toCanonicalKType() }
  if (typeArguments.isEmpty()) return null
  if (classId == K_CLASS_ID) return null
  return withArguments { ConeStarProjection }.createKType(typeArguments.asList())
}

context(c: SessionHolder)
fun ConeKotlinType.createKType(typeArguments: List<ConeTypeProjection>) =
  typeArguments.fold(this) { acc, arg -> K_CLASS_ID.createConeType(c.session, arrayOf(acc, arg)) }

context(_: SessionHolder)
fun ConeKotlinType.applyKAndCanonicalize(): ConeKotlinType = intersect(applyKAndCanonicalizeOrNull())

context(_: SessionHolder)
fun ConeKotlinType.applyKAndCanonicalizeOrNull(): ConeKotlinType? = applyKEverywhere()?.let {
  it.intersect(it.toCanonicalKType(), toCanonicalKType())
} ?: toCanonicalKType()

context(c: SessionHolder)
private fun ConeKotlinType.intersect(vararg types: ConeKotlinType?) =
  ConeTypeIntersector.intersectTypes(c.session.typeContext, listOf(this) + types.filterNotNull())

context(c: SessionHolder)
private inline fun ConeIntersectionType.mapTypesNotNull(func: (ConeKotlinType) -> ConeKotlinType?): ConeKotlinType? =
  ConeTypeIntersector.intersectTypes(c.session.typeContext, intersectedTypes.mapNotNull(func).ifEmpty { return null })
