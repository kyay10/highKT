package io.github.kyay10.highkt.fir

import kotlin.collections.fold
import org.jetbrains.kotlin.fir.SessionHolder
import org.jetbrains.kotlin.fir.plugin.createConeType
import org.jetbrains.kotlin.fir.resolve.directExpansionType
import org.jetbrains.kotlin.fir.resolve.fullyExpandedType
import org.jetbrains.kotlin.fir.resolve.toSymbol
import org.jetbrains.kotlin.fir.types.AbbreviatedTypeAttribute
import org.jetbrains.kotlin.fir.types.ConeAttributes
import org.jetbrains.kotlin.fir.types.ConeClassLikeLookupTag
import org.jetbrains.kotlin.fir.types.ConeClassLikeType
import org.jetbrains.kotlin.fir.types.ConeFlexibleType
import org.jetbrains.kotlin.fir.types.ConeIntersectionType
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.ConeKotlinTypeConflictingProjection
import org.jetbrains.kotlin.fir.types.ConeKotlinTypeProjectionIn
import org.jetbrains.kotlin.fir.types.ConeKotlinTypeProjectionOut
import org.jetbrains.kotlin.fir.types.ConeRigidType
import org.jetbrains.kotlin.fir.types.ConeStarProjection
import org.jetbrains.kotlin.fir.types.ConeTypeIntersector
import org.jetbrains.kotlin.fir.types.ConeTypeProjection
import org.jetbrains.kotlin.fir.types.abbreviatedType
import org.jetbrains.kotlin.fir.types.abbreviatedTypeOrSelf
import org.jetbrains.kotlin.fir.types.classId
import org.jetbrains.kotlin.fir.types.classLikeLookupTagIfAny
import org.jetbrains.kotlin.fir.types.constructClassType
import org.jetbrains.kotlin.fir.types.constructType
import org.jetbrains.kotlin.fir.types.isMarkedNullable
import org.jetbrains.kotlin.fir.types.renderForDebugging
import org.jetbrains.kotlin.fir.types.toTrivialFlexibleType
import org.jetbrains.kotlin.fir.types.type
import org.jetbrains.kotlin.fir.types.typeContext
import org.jetbrains.kotlin.fir.types.withArguments
import org.jetbrains.kotlin.fir.types.withAttributes
import org.jetbrains.kotlin.fir.types.withNullability

data class FunctionApplicationRhs(val arg: ConeTypeProjection, val attributes: ConeAttributes)

/**
 * Guarantees !realType.isK originated from K<...@applications[1].attributes K<@applications[0].attributes K<symbol,
 * applications[0].arg>, applications[1].arg>...>
 */
class KType(val type: ConeKotlinType, val applications: List<FunctionApplicationRhs>) {
  val lookupTagAndArity: Pair<ConeClassLikeLookupTag, Int>?
    get() =
      when (type.classId) {
        CONSTRUCTOR_CLASS_ID ->
          type.typeArguments.singleOrNull()?.type?.abbreviatedTypeOrSelf?.let {
            it.classLikeLookupTagIfAny toN it.typeArguments.size
          }
        IDENTITY_CLASS_ID -> type.classLikeLookupTagIfAny toN 1
        else -> null
      }

  context(_: SessionHolder)
  fun applyOnce(): KType? {
    if (applications.isEmpty()) return null
    val (lookupTag, arity) = lookupTagAndArity ?: return null
    if (arity == 0) return null // only happens in malformed IDE examples
    val isIdentity: Boolean = lookupTag.classId == IDENTITY_CLASS_ID
    val (immediatelyUsableTypes, extraTypes) = applications.splitAtOrNull(arity) ?: return null // partially applied
    val attributes =
      immediatelyUsableTypes
        .last()
        .attributes
        .remove(AbbreviatedTypeAttribute::class)
        .remove(ExpandedTypeAttribute::class)
    val substituted =
      if (isIdentity) immediatelyUsableTypes[0].arg.outType?.withCombinedAttributesFrom(attributes) ?: return null
      else
        lookupTag
          .constructClassType(immediatelyUsableTypes.map { it.arg }.toTypedArray(), attributes = attributes)
          .fullyExpandedTypeWithAttribute()
    return substituted.withExpanded(KType(type, immediatelyUsableTypes).toType()).toKType()?.addApplications(extraTypes)
  }

  context(c: SessionHolder)
  fun toType(): ConeKotlinType =
    applications.fold(type) { acc, app ->
      K_CLASS_ID.createConeType(c.session, arrayOf(acc, app.arg)).withAttributes(app.attributes)
    }

  fun addApplications(newApplications: List<FunctionApplicationRhs>) = KType(type, applications + newApplications)

  override fun toString(): String =
    if (applications.isEmpty()) type.renderForDebugging()
    else
      buildString {
        append("K")
        append(applications.size)
        append("<")
        append(type.renderForDebugging())
        if (applications.isNotEmpty()) {
          append(", ")
          append(applications.joinToString(", ") { (arg, _) -> arg.type?.renderForDebugging() ?: "*" })
        }
        append(">")
      }
}

private val ConeTypeProjection.outType
  get() =
    when (this) {
      is ConeKotlinTypeProjectionOut -> type
      is ConeKotlinType -> this
      is ConeKotlinTypeConflictingProjection,
      is ConeKotlinTypeProjectionIn,
      ConeStarProjection -> null
    }

context(c: SessionHolder)
private fun ConeKotlinType.toKType(): KType? {
  var realType: ConeKotlinType = fullyExpandedType()
  val appliedTypes = buildList {
    while (realType.isK) {
      add(0, FunctionApplicationRhs(realType.typeArguments[1], realType.attributes))
      realType = realType.typeArguments[0].type?.fullyExpandedType() ?: return null // Caused by malformed K type
    }
  }
  return KType(realType, appliedTypes)
}

context(c: SessionHolder)
fun ConeClassLikeType.fullyExpandedTypeWithAttribute(): ConeClassLikeType {
  val directExpansionType = directExpansionType(c.session) ?: return this
  val intermediate = directExpansionType.abbreviatedType?.toCanonicalKType()
  val expansion = directExpansionType.fullyExpandedTypeWithAttribute()
  // TODO decide if this is right
  if (expansion.isK) return expansion
  return intermediate?.let(expansion::withExpanded) ?: expansion
}

fun <T : ConeKotlinType> T.withExpanded(expanded: ConeKotlinType?): T =
  withAttributes(attributes.withExpanded(expanded))

private fun ConeAttributes.withExpanded(expanded: ConeKotlinType?): ConeAttributes =
  if (expanded == null) this else add(ExpandedTypeAttribute(expandedType?.withExpanded(expanded) ?: expanded))

context(c: SessionHolder)
private fun ConeKotlinType.applyTypeFunctions(): ConeKotlinType {
  if (shouldLeaveUnevaluated) return this
  val kType = toKType()?.takeIf { it.applications.isNotEmpty() } ?: return this
  val applied = kType.apply()
  return if (applied === kType) this else applied.toType().addNullability(isMarkedNullable)
}

context(c: SessionHolder)
private tailrec fun KType.apply(): KType = (applyOnce() ?: return this).apply()

private fun <T> List<T>.splitAtOrNull(index: Int): Pair<List<T>, List<T>>? {
  if (index !in 0..size) return null
  return subList(0, index) to subList(index, size)
}

context(c: SessionHolder)
private fun ConeKotlinType.applyK(): ConeKotlinType? {
  if (this is ConeFlexibleType && isTrivial)
    return (lowerBound.applyK() as? ConeRigidType)?.toTrivialFlexibleType(c.session.typeContext)
  if (this is ConeFlexibleType) {
    val newLowerBound = lowerBound.applyK() as? ConeRigidType
    val newUpperBound = upperBound.applyK() as? ConeRigidType
    if (newLowerBound != null || newUpperBound != null)
      return ConeFlexibleType(newLowerBound ?: lowerBound, newUpperBound ?: upperBound, false)
  }
  if (this is ConeIntersectionType) return mapTypesNotNull { it.applyK() }
  if (typeArguments.isEmpty()) return null
  return applyTypeFunctions().takeIf { it !== this }
}

context(c: SessionHolder)
fun ConeKotlinType.applyKOrSelf(): ConeKotlinType = applyK() ?: this

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
  if (isK) return this
  val symbol = toSymbol() ?: return null
  val constructor = symbol.constructType(Array(typeArguments.size) { ConeStarProjection })
  val args = typeArguments.map { FunctionApplicationRhs(it, ConeAttributes.Empty) }
  return KType(CONSTRUCTOR_CLASS_ID.createConeType(c.session, arrayOf(constructor)), args)
    .toType()
    .addNullability(isMarkedNullable, attributes)
}

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

fun <T : ConeKotlinType> T.withArgumentsSafe(replacement: Array<out ConeTypeProjection>): T =
  if (typeArguments.isEmpty()) this else withArguments(replacement)

context(c: SessionHolder)
private fun <T : ConeKotlinType> T.addNullability(nullable: Boolean, attributes: ConeAttributes = this.attributes): T =
  withNullability(nullable || isMarkedNullable, c.session.typeContext, attributes, preserveAttributes = true)

private fun ConeKotlinType.withCombinedAttributesFrom(other: ConeAttributes): ConeKotlinType {
  if (other.isEmpty()) return this
  val combinedConeAttributes = attributes.add(other)
  return withAttributes(combinedConeAttributes)
}

private infix fun <A, B> A.toN(other: B): Pair<A & Any, B & Any>? = (this ?: return null) to (other ?: return null)
