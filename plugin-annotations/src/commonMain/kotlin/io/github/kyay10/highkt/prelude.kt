@file:OptIn(ExperimentalContracts::class)

package io.github.kyay10.highkt

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

public interface K<F, A>
public typealias K2<F, A, B> = K<K<F, A>, B>
public typealias K3<F, A, B, C> = K<K2<F, A, B>, C>
public typealias K4<F, A, B, C, D> = K<K3<F, A, B, C>, D>
public typealias K5<F, A, B, C, D, E> = K<K4<F, A, B, C, D>, E>
public typealias K6<F, A, B, C, D, E, G> = K<K5<F, A, B, C, D, E>, G>
public typealias K7<F, A, B, C, D, E, G, H> = K<K6<F, A, B, C, D, E, G>, H>
public typealias K8<F, A, B, C, D, E, G, H, I> = K<K7<F, A, B, C, D, E, G, H>, I>
public typealias K9<F, A, B, C, D, E, G, H, I, J> = K<K8<F, A, B, C, D, E, G, H, I>, J>
public typealias K10<F, A, B, C, D, E, G, H, I, J, L> = K<K9<F, A, B, C, D, E, G, H, I, J>, L>

public inline fun fix(vararg casts: Unit) {}
public val Unit.all: Unit get() = this

public inline fun <reified T> assertIsType(x: Any?) {
  contract {
    returns() implies (x is T)
  }
}