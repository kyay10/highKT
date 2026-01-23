package io.github.kyay10.highkt.fir

import org.jetbrains.kotlin.fir.SessionHolder
import org.jetbrains.kotlin.fir.plugin.createConeType
import org.jetbrains.kotlin.fir.resolve.directExpansionType
import org.jetbrains.kotlin.fir.resolve.fullyExpandedType
import org.jetbrains.kotlin.fir.resolve.toClassLikeSymbol
import org.jetbrains.kotlin.fir.resolve.toSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassLikeSymbol
import org.jetbrains.kotlin.fir.types.AbbreviatedTypeAttribute
import org.jetbrains.kotlin.fir.types.ConeAttributes
import org.jetbrains.kotlin.fir.types.ConeClassLikeType
import org.jetbrains.kotlin.fir.types.ConeFlexibleType
import org.jetbrains.kotlin.fir.types.ConeIntersectionType
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.ConeRigidType
import org.jetbrains.kotlin.fir.types.ConeStarProjection
import org.jetbrains.kotlin.fir.types.ConeTypeIntersector
import org.jetbrains.kotlin.fir.types.ConeTypeProjection
import org.jetbrains.kotlin.fir.types.abbreviatedType
import org.jetbrains.kotlin.fir.types.abbreviatedTypeOrSelf
import org.jetbrains.kotlin.fir.types.classId
import org.jetbrains.kotlin.fir.types.constructType
import org.jetbrains.kotlin.fir.types.isMarkedNullable
import org.jetbrains.kotlin.fir.types.isStarProjection
import org.jetbrains.kotlin.fir.types.lowerBoundIfFlexible
import org.jetbrains.kotlin.fir.types.replaceType
import org.jetbrains.kotlin.fir.types.toTrivialFlexibleType
import org.jetbrains.kotlin.fir.types.type
import org.jetbrains.kotlin.fir.types.typeContext
import org.jetbrains.kotlin.fir.types.withArguments
import org.jetbrains.kotlin.fir.types.withAttributes
import org.jetbrains.kotlin.fir.types.withNullability

context(c: SessionHolder)
private fun ConeKotlinType.deconstructIfKType(): Pair<FirClassLikeSymbol<*>, List<ConeTypeProjection>>? {
  var realType: ConeKotlinType = fullyExpandedType()
  val appliedTypes = buildList {
    while (realType.isK) {
      add(0, realType.typeArguments[1])
      realType = realType.typeArguments[0].type?.fullyExpandedType() ?: return null // Caused by malformed K type
    }
    // Now realType is not a K
  }
  if (appliedTypes.isEmpty()) return null // implies this was not a K at all
  if (realType.lowerBoundIfFlexible() is ConeClassLikeType && realType.classId != IDENTITY_CLASS_ID) {
    // so type parameters are allowed
    if (realType.classId != CONSTRUCTOR_CLASS_ID) return null // Must be Constructor
    realType = realType.typeArguments.firstOrNull()?.type?.abbreviatedTypeOrSelf ?: return null
  }
  // Could be relaxed
  if (realType.typeArguments.any { !it.isStarProjection }) return null
  return realType.toClassLikeSymbol()?.let { it to appliedTypes }
}

context(c: SessionHolder)
private fun ConeClassLikeType.fullyExpandedTypeWithAttribute(): ConeClassLikeType {
  val directExpansionType = directExpansionType(c.session) ?: return this
  val expansion = directExpansionType.fullyExpandedTypeWithAttribute()
  return expansion.withExpanded(this, directExpansionType.abbreviatedType?.takeIf { !it.isK })
}

private fun <T : ConeKotlinType> T.withExpanded(expanded: ConeKotlinType, intermediate: ConeKotlinType?): T =
  withAttributes(attributes.withExpanded(expanded, intermediate))

private fun ConeAttributes.withExpanded(expanded: ConeKotlinType, intermediate: ConeKotlinType?): ConeAttributes =
  add(
    ExpandedTypeAttribute(
      intermediate?.withExpanded(expanded, intermediate.attributes.expandedType?.coneType.takeIf { it !== expanded })
        ?: expanded
    )
  )

context(c: SessionHolder)
private tailrec fun ConeKotlinType.applyTypeFunctions(): ConeKotlinType {
  if (attributes.shouldLeaveUnevaluated) return this
  val (symbol, appliedTypes) = deconstructIfKType() ?: return this
  val isIdentity = symbol.classId == IDENTITY_CLASS_ID
  val typeParameterSize = if (isIdentity) 1 else symbol.ownTypeParameterSymbols.size
  val (immediatelyUsableTypes, extraTypes) =
    appliedTypes.splitAtOrNull(typeParameterSize) ?: return this // partially-applied type
  // TODO what if K<Identity, in/out A>?
  val substituted =
    if (isIdentity) immediatelyUsableTypes.single().type ?: return this
    else symbol.constructType(immediatelyUsableTypes.toTypedArray()).fullyExpandedTypeWithAttribute()
  val newKType =
    substituted
      .createKType(extraTypes)
      .withCombinedAttributesFrom(
        attributes.remove(ExpandedTypeAttribute::class).remove(AbbreviatedTypeAttribute::class)
      )
  val newAttributes = newKType.attributes.withExpanded(this, newKType.attributes.expandedType?.coneType)
  return newKType.addNullability(isMarkedNullable, newAttributes).applyTypeFunctions()
}

private fun <T> List<T>.splitAtOrNull(index: Int): Pair<List<T>, List<T>>? {
  if (index !in 0..size) return null
  return subList(0, index) to subList(index, size)
}

// TODO: application order?
context(c: SessionHolder)
private fun ConeKotlinType.applyKEverywhere(): ConeKotlinType? {
  if (this is ConeFlexibleType && isTrivial)
    return (lowerBound.applyKEverywhere() as? ConeRigidType)?.toTrivialFlexibleType(c.session.typeContext)
  if (this is ConeFlexibleType) {
    val newLowerBound = lowerBound.applyKEverywhere() as? ConeRigidType
    val newUpperBound = upperBound.applyKEverywhere() as? ConeRigidType
    if (newLowerBound != null || newUpperBound != null)
      return ConeFlexibleType(newLowerBound ?: lowerBound, newUpperBound ?: upperBound, false)
  }
  if (this is ConeIntersectionType) return mapTypesNotNull { it.applyKEverywhere() }
  if (typeArguments.isEmpty()) return null
  val appliedOutside = applyTypeFunctions()
  var replacedAny = appliedOutside !== this
  return appliedOutside
    .withArgumentsSafe { arg ->
      arg.type?.applyKEverywhere()?.let {
        replacedAny = true
        arg.replaceType(it)
      } ?: arg
    }
    .takeIf { replacedAny }
}

context(c: SessionHolder)
fun ConeKotlinType.applyKOrSelf(): ConeKotlinType = applyKEverywhere() ?: this

context(c: SessionHolder)
fun ConeKotlinType.toCanonicalKType(): ConeKotlinType? {
  if (this is ConeFlexibleType && isTrivial)
    return (lowerBound.toCanonicalKType() as ConeRigidType?)?.toTrivialFlexibleType(c.session.typeContext)
  if (this is ConeFlexibleType) {
    val newLowerBound = lowerBound.toCanonicalKType() as ConeRigidType?
    val newUpperBound = upperBound.toCanonicalKType() as ConeRigidType?
    if (newLowerBound != null || newUpperBound != null)
      return ConeFlexibleType(newLowerBound ?: lowerBound, newUpperBound ?: upperBound, false)
  }
  if (this is ConeIntersectionType) return mapTypesNotNull { it.toCanonicalKType() }
  if (typeArguments.isEmpty()) return null
  if (isK) return null
  val symbol = toSymbol() ?: return null
  val constructor = symbol.constructType(Array(typeArguments.size) { ConeStarProjection })
  return CONSTRUCTOR_CLASS_ID.createConeType(c.session, arrayOf(constructor))
    .createKType(typeArguments.asList())
    .addNullability(isMarkedNullable, attributes)
}

context(c: SessionHolder)
private fun ConeKotlinType.createKType(typeArguments: List<ConeTypeProjection>) =
  typeArguments.fold(this) { acc, arg -> K_CLASS_ID.createConeType(c.session, arrayOf(acc, arg)) }

context(_: SessionHolder)
val ConeKotlinType?.isK
  get() = this != null && fullyExpandedType().classId == K_CLASS_ID

context(c: SessionHolder)
private inline fun ConeIntersectionType.mapTypesNotNull(func: (ConeKotlinType) -> ConeKotlinType?): ConeKotlinType? =
  ConeTypeIntersector.intersectTypes(
      c.session.typeContext,
      intersectedTypes.mapNotNull(func).ifEmpty {
        return null
      },
    )
    .withUpperBound(upperBoundForApproximation?.let(func) ?: upperBoundForApproximation)

private fun ConeKotlinType.withUpperBound(upper: ConeKotlinType?): ConeKotlinType =
  if (this is ConeIntersectionType) ConeIntersectionType(intersectedTypes, upper) else this

private inline fun <T : ConeKotlinType> T.withArgumentsSafe(
  replacement: (ConeTypeProjection) -> ConeTypeProjection
): T = if (typeArguments.isEmpty()) this else withArguments(replacement)

context(c: SessionHolder)
private fun <T : ConeKotlinType> T.addNullability(nullable: Boolean, attributes: ConeAttributes = this.attributes): T =
  withNullability(nullable || isMarkedNullable, c.session.typeContext, attributes, preserveAttributes = true)

private fun ConeKotlinType.withCombinedAttributesFrom(other: ConeAttributes): ConeKotlinType {
  if (other.isEmpty()) return this
  val combinedConeAttributes = attributes.add(other)
  return withAttributes(combinedConeAttributes)
}
