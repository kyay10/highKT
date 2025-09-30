@file:OptIn(ExperimentalContracts::class)

package io.github.kyay10.highkt

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

public interface K<out F, A>
public interface KOut<out F, out A>: K<F, @UnsafeVariance A>
public interface KIn<out F, in A>: K<F, @UnsafeVariance A>

public typealias KBi<F, A, B> = KOut<KOut<F, A>, B>
public typealias KPro<F, A, B> = KOut<KIn<F, A>, B>
public typealias K2<F, A, B> = K<K<F, A>, B>
public typealias K3<F, A, B, C> = K<K2<F, A, B>, C>
public typealias K4<F, A, B, C, D> = K<K3<F, A, B, C>, D>
public typealias K5<F, A, B, C, D, E> = K<K4<F, A, B, C, D>, E>

public inline fun fix(vararg casts: Unit) {}
public val Unit.all: Unit get() = this

public inline fun <reified T> assertIsType(x: Any?) {
  contract {
    returns() implies (x is T)
  }
}