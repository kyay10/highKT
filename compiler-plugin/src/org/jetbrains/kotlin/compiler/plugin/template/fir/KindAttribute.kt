package org.jetbrains.kotlin.compiler.plugin.template.fir

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.expressions.FirAnnotation
import org.jetbrains.kotlin.fir.expressions.builder.buildAnnotation
import org.jetbrains.kotlin.fir.expressions.impl.FirEmptyAnnotationArgumentMapping
import org.jetbrains.kotlin.fir.extensions.FirTypeAttributeExtension
import org.jetbrains.kotlin.fir.types.ConeAttribute
import org.jetbrains.kotlin.fir.types.ConeAttributeWithConeType
import org.jetbrains.kotlin.fir.types.ConeAttributes
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.builder.buildResolvedTypeRef
import org.jetbrains.kotlin.fir.types.classId
import org.jetbrains.kotlin.fir.types.coneTypeOrNull
import org.jetbrains.kotlin.fir.types.impl.ConeClassLikeTypeImpl
import org.jetbrains.kotlin.fir.types.renderReadable
import org.jetbrains.kotlin.fir.types.toConeTypeProjection
import org.jetbrains.kotlin.fir.types.toLookupTag
import org.jetbrains.kotlin.fir.types.toTypeProjection
import org.jetbrains.kotlin.fir.types.type
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.types.Variance

data class KindAttribute(override val coneType: ConeKotlinType) : ConeAttributeWithConeType<KindAttribute>() {
  override fun copyWith(newType: ConeKotlinType) =
    copy(coneType = newType)
  override fun add(other: KindAttribute?) = other ?: this
  override fun intersect(other: KindAttribute?) = null
  override fun isSubtypeOf(other: KindAttribute?) = this == other
  override fun renderForReadability() = "<${coneType.renderReadable()}>"
  override fun union(other: KindAttribute?) = null
  override val implementsEquality get() = true
  override val keepInInferredDeclarationType get() = true
  override val key get() = KindAttribute::class
}

class KindAttributeExtension(session: FirSession): FirTypeAttributeExtension(session) {
  companion object {
    private val PACKAGE_FQN = FqName("org.jetbrains.kotlin.compiler.plugin.template")
    private val K_CLASS_ID = ClassId(PACKAGE_FQN, Name.identifier("K"))
  }

  override fun convertAttributeToAnnotation(attribute: ConeAttribute<*>): FirAnnotation? {
    if (attribute !is KindAttribute) return null
    val typeRef = attribute.coneType
    return buildAnnotation {
      annotationTypeRef = buildResolvedTypeRef {
        coneType = ConeClassLikeTypeImpl(
          K_CLASS_ID.toLookupTag(),
          arrayOf(typeRef.toTypeProjection(Variance.INVARIANT)),
          isMarkedNullable = false
        )
      }
      argumentMapping = FirEmptyAnnotationArgumentMapping
    }
  }

  override fun extractAttributeFromAnnotation(annotation: FirAnnotation): ConeAttribute<*>? {
    if (annotation.annotationTypeRef.coneTypeOrNull?.classId != K_CLASS_ID) return null
    val typeArgument = annotation.typeArguments.singleOrNull()?.toConeTypeProjection()?.type ?: return null
    return KindAttribute(typeArgument)
  }
}

val ConeAttributes.kind: KindAttribute? by ConeAttributes.attributeAccessor<KindAttribute>()

val ConeKotlinType.kindArgument: ConeKotlinType?
  get() = attributes.kind?.coneType