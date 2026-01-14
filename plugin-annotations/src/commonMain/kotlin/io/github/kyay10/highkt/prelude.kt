@file:Suppress("NOTHING_TO_INLINE", "unused")

package io.github.kyay10.highkt

public sealed interface Constructor<C>

// Represents the identity type function
public typealias Id<A> = Out<Identity, A>
public object Identity

// Represents function application
public interface K<out F, A>

public typealias Out<F, A> = K<F, out A>
public typealias In<F, A> = K<F, in A>

public typealias Bi<F, A, B> = Out<Out<F, A>, B>
public typealias Pro<F, A, B> = Out<In<F, A>, B>
public typealias K2<F, A, B> = K<K<F, A>, B>

public typealias Tri<F, A, B, C> = Out<Bi<F, A, B>, C>
public typealias K3<F, A, B, C> = K<K2<F, A, B>, C>

public inline fun <T> T.expandTo(): T = this