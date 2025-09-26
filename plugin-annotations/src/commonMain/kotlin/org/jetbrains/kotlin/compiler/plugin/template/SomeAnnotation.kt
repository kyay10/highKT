@file:OptIn(ExperimentalContracts::class)

package org.jetbrains.kotlin.compiler.plugin.template

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

@MustBeDocumented
@Target(AnnotationTarget.TYPE, AnnotationTarget.VALUE_PARAMETER)
public annotation class K<A>

public inline fun fixAll(vararg casts: Unit) {}

public inline fun <reified T> assertIsType(x: Any?) {
  contract {
    returns() implies (x is T)
  }
}