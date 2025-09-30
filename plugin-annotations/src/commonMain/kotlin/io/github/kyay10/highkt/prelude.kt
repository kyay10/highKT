@file:OptIn(ExperimentalContracts::class)

package io.github.kyay10.highkt

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

public interface Out<out F, out A>
public interface In<out F, in A>
public interface K<out F, A>: Out<F, A>, In<F, A>

public typealias Bi<F, A, B> = Out<Out<F, A>, B>
public typealias Pro<F, A, B> = Out<In<F, A>, B>
public typealias K2<F, A, B> = K<K<F, A>, B>
public typealias Tri<F, A, B, C> = Out<Bi<F, A, B>, C>
public typealias K3<F, A, B, C> = K<K2<F, A, B>, C>

@Suppress("NOTHING_TO_INLINE")
public inline fun fix(vararg casts: Unit) {}
public val Unit.all: Unit get() = this

public inline fun <reified T> assertIsType(x: Any?) {
  contract {
    returns() implies (x is T)
  }
}