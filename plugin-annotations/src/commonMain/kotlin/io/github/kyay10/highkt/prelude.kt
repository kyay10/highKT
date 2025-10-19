@file:OptIn(ExperimentalContracts::class)
@file:Suppress("NOTHING_TO_INLINE")

package io.github.kyay10.highkt

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract
import kotlin.js.JsName
import kotlin.jvm.JvmName

@Target(AnnotationTarget.CLASS)
public annotation class TypeFunction

// Special type-lambda to represent identity type constructor
@TypeFunction
public interface Id<@Suppress("unused") A>
public typealias Identity = Id<*>

@Suppress("unused")
public interface K<out F, A>

public typealias Out<F, A> = K<F, out A>
public typealias In<F, A> = K<F, in A>

public typealias Bi<F, A, B> = Out<Out<F, A>, B>
public typealias Pro<F, A, B> = Out<In<F, A>, B>
public typealias K2<F, A, B> = K<K<F, A>, B>

public typealias Tri<F, A, B, C> = Out<Bi<F, A, B>, C>
public typealias K3<F, A, B, C> = K<K2<F, A, B>, C>

public inline fun <T> assertIsType(x: Any?) {
  contract {
    returns() implies (x is T)
  }
}

@JvmName("assertIsType2")
@JsName("assertIsType2")
public inline fun <T1, T2> assertIsType(x: Any?) {
  contract {
    returns() implies (x is T1 && x is T2)
  }
}

public inline fun <T> Any?.expandTo(): T {
  @Suppress("UNCHECKED_CAST")
  return this as T
}