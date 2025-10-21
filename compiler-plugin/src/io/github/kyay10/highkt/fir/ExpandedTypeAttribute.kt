package io.github.kyay10.highkt.fir

import org.jetbrains.kotlin.fir.types.ConeAttributeWithConeType
import org.jetbrains.kotlin.fir.types.ConeAttributes
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.renderForDebugging
import org.jetbrains.kotlin.fir.types.renderReadable

class ExpandedTypeAttribute(override val coneType: ConeKotlinType) : ConeAttributeWithConeType<ExpandedTypeAttribute>() {
  override fun copyWith(newType: ConeKotlinType) =
    ExpandedTypeAttribute(coneType = newType)
  override fun add(other: ExpandedTypeAttribute?) = other ?: this
  override fun intersect(other: ExpandedTypeAttribute?) = other ?: this
  override fun isSubtypeOf(other: ExpandedTypeAttribute?) = true
  override fun renderForReadability() = "${coneType.renderReadable()} ~> "
  override fun toString(): String = "{${coneType.renderForDebugging()} ~>}"
  override fun union(other: ExpandedTypeAttribute?) = other ?: this
  override val keepInInferredDeclarationType get() = true
  override val key get() = ExpandedTypeAttribute::class
}

val ConeAttributes.expandedType by ConeAttributes.attributeAccessor<ExpandedTypeAttribute>()