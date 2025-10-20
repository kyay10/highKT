package io.github.kyay10.highkt.fir

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.toAnnotationClassId
import org.jetbrains.kotlin.fir.expressions.FirAnnotation
import org.jetbrains.kotlin.fir.expressions.builder.buildAnnotation
import org.jetbrains.kotlin.fir.expressions.impl.FirEmptyAnnotationArgumentMapping
import org.jetbrains.kotlin.fir.extensions.FirTypeAttributeExtension
import org.jetbrains.kotlin.fir.types.ConeAttribute
import org.jetbrains.kotlin.fir.types.builder.buildResolvedTypeRef
import org.jetbrains.kotlin.fir.types.constructClassType
import org.jetbrains.kotlin.fir.types.toLookupTag

class LeaveUnevaluatedAttributeExtension(session: FirSession) : FirTypeAttributeExtension(session) {
  override fun convertAttributeToAnnotation(attribute: ConeAttribute<*>) = if (attribute is LeaveUnevaluatedAttribute) {
    buildAnnotation {
      annotationTypeRef = buildResolvedTypeRef {
        coneType = LEAVE_UNEVALUATED_CLASS_ID.toLookupTag().constructClassType()
      }
      argumentMapping = FirEmptyAnnotationArgumentMapping
    }
  } else null

  override fun extractAttributeFromAnnotation(annotation: FirAnnotation) = LeaveUnevaluatedAttribute.takeIf {
    annotation.toAnnotationClassId(session) == LEAVE_UNEVALUATED_CLASS_ID
  }
}

data object LeaveUnevaluatedAttribute : ConeAttribute<LeaveUnevaluatedAttribute>() {
  override fun union(other: LeaveUnevaluatedAttribute?) = other
  override fun intersect(other: LeaveUnevaluatedAttribute?) = this
  override fun add(other: LeaveUnevaluatedAttribute?) = this

  override fun isSubtypeOf(other: LeaveUnevaluatedAttribute?) = true

  override val key = LeaveUnevaluatedAttribute::class
  override val keepInInferredDeclarationType: Boolean get() = true
}