package foo.bar

// LANGUAGE: +ContextParameters

import io.github.kyay10.highkt.*

interface Functor<F> {
  fun <A, B> K<F, A>.fmap(f: (A) -> B): K<F, B>
}

context(functor: Functor<F>)
fun <F, A, B> K<F, A>.fmap(f: (A) -> B) = with(functor) { fmap(f) }

interface BiFunctor<F> {
  fun <A, B, C, D> K2<F, A, B>.bimap(f: (A) -> C, g: (B) -> D): K2<F, C, D> = leftMap(f).rightMap(g)
  fun <A, B, C> K2<F, A, B>.leftMap(f: (A) -> C): K2<F, C, B> = bimap(f) { it }
  fun <A, B, D> K2<F, A, B>.rightMap(g: (B) -> D): K2<F, A, D> = bimap({ it }, g)
}

context(b: BiFunctor<F>)
fun <A, B, C, D, F> K2<F, A, B>.bimap(f: (A) -> C, g: (B) -> D) = with(b) { bimap(f, g) }

data class BiComposed<Bi, F, G, A, B>(val value: K2<Bi, K<F, A>, K<G, B>>) : K2<BiCompose<Bi, F, G>, A, B>
typealias BiCompose<Bi, F, G> = K3<BiComposed<*, *, *, *, *>, Bi, F, G>

context(_: BiFunctor<BF>, _: Functor<F>, _: Functor<G>)
fun <BF, F, G> composeBiFunctors() = object : BiFunctor<BiCompose<BF, F, G>> {
  override fun <A, B, C, D> K2<BiCompose<BF, F, G>, A, B>.bimap(f: (A) -> C, g: (B) -> D): K2<BiCompose<BF, F, G>, C, D> {
    fix().all = 42
    return BiComposed(value.bimap({ it.fmap(f) }) { it.fmap(g) })
  }
}

fun box(): String {
  return "OK"
}
