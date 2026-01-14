package foo.bar

// LANGUAGE: +ContextParameters

import io.github.kyay10.highkt.*

class Prompt<R>

interface Monad<F> {
  fun <A> pure(a: A): K<F, A>
  fun <A, B> K<F, A>.flatMap(f: suspend (A) -> K<F, B>): K<F, B>
}

context(m: Monad<F>)
fun <F, A> pure(a: A): K<F, A> = m.pure(a)

context(m: Monad<F>)
fun <F, A, B> K<F, A>.flatMap(f: suspend (A) -> K<F, B>): K<F, B> = with(m) { this@flatMap.flatMap(f) }

context(_: Prompt<K<F, A>>, _: Monad<F>)
suspend fun <F, A, B> K<F, B>.bind(): B = TODO()
suspend fun <F, A, B> b(m: Monad<F>, prompt: Prompt<K<F, A>>, a: K<F, B>): B = context(m, prompt) { a.bind() }

data class State<S, out A>(val run: suspend (S) -> Pair<A, S>)
typealias StateOf<S> = K<Constructor<State<*, *>>, S>

context(m: Monad<StateOf<S>>)
suspend fun <S, A, B> Prompt<K<StateOf<S>, A>>.b2(a: K<StateOf<S>, B>): B =
  b<StateOf<S>, A, B>(m, this, a)

context(m: Monad<StateOf<S>>)
suspend fun <S, A, B> Prompt<State<S, A>>.b3(a: K<StateOf<S>, B>): B =
  b<StateOf<S>, A, B>(m, this, a)

fun box(): String {
  return "OK"
}
