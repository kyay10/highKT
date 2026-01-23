package io.github.kyay10.highkt.fir

import org.jetbrains.kotlin.fir.types.ConeAttribute
import org.jetbrains.kotlin.fir.types.ConeAttributeWithConeType
import org.jetbrains.kotlin.fir.types.ConeAttributes
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.renderForDebugging
import org.jetbrains.kotlin.fir.types.renderReadable
import org.jetbrains.kotlin.fir.types.withAttributes

class ExpandedTypeAttribute(override val coneType: ConeKotlinType) : ConeAttributeWithConeType<ExpandedTypeAttribute>() {
  override fun copyWith(newType: ConeKotlinType) =
    ExpandedTypeAttribute(coneType = newType)
  override fun add(other: ExpandedTypeAttribute?) = other ?: this
  override fun intersect(other: ExpandedTypeAttribute?) = other ?: this
  override fun isSubtypeOf(other: ExpandedTypeAttribute?) = true
  override fun renderForReadability() = "${coneType.renderReadable()} ~> "
  override fun toString(): String = buildList {
    var current: ExpandedTypeAttribute? = this@ExpandedTypeAttribute
    while (current != null) {
      val attrs = current.coneType.attributes
      add(0, current.coneType.withAttributes(attrs.remove(ExpandedTypeAttribute::class)))
      current = attrs.expandedType
    }
  }.joinToString(" ~> ", prefix = "{", postfix = " ~>}") { it.renderForDebugging() }

  override fun union(other: ExpandedTypeAttribute?) = other ?: this
  override val keepInInferredDeclarationType get() = true
  override val key get() = ExpandedTypeAttribute::class
}

val ConeAttributes.expandedType by ConeAttributes.attributeAccessor<ExpandedTypeAttribute>()

data object LeaveUnevaluatedAttribute : ConeAttribute<LeaveUnevaluatedAttribute>() {
  override fun add(other: LeaveUnevaluatedAttribute?) = other ?: this
  override fun intersect(other: LeaveUnevaluatedAttribute?) = other ?: this

  override fun isSubtypeOf(other: LeaveUnevaluatedAttribute?) = true

  override fun union(other: LeaveUnevaluatedAttribute?) = other ?: this
  override val key = LeaveUnevaluatedAttribute::class
  override val keepInInferredDeclarationType: Boolean get() = false
  override fun renderForReadability() = "[LeaveUnevaluated]"
  override fun toString(): String = "{LeaveUnevaluated}"
}

val ConeAttributes.leaveUnevaluated by ConeAttributes.attributeAccessor<LeaveUnevaluatedAttribute>()
val ConeAttributes.shouldLeaveUnevaluated get() = this.leaveUnevaluated != null