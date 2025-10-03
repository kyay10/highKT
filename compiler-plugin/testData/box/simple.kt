package foo.bar

// LANGUAGE: +ContextParameters

import io.github.kyay10.highkt.*

interface Functor<F> {
  fun <A, B> K<F, A>.fmap(f: (A) -> B): K<F, B>
}

interface Monad<M> : Functor<M> {
  fun <A> pure(a: A): K<M, A>

  fun <A, B> K<M, A>.bind(f: (A) -> K<M, B>): K<M, B>

  override fun <A, B> K<M, A>.fmap(f: (A) -> B) = bind { a -> pure(f(a)) }
}

context(functor: Functor<F>)
fun <F, A, B> K<F, A>.fmap(f: (A) -> B) = with(functor) { fmap(f) }

context(monad: Monad<M>)
fun <M, A> pure(a: A) = with(monad) { pure(a) }

context(monad: Monad<M>)
fun <M, A, B> K<M, A>.bind(f: (A) -> K<M, B>) = with(monad) { bind(f) }

class ListK<A>(list: List<A>) : K<ListK<*>, A>, List<A> by list, AbstractList<A>()

fun <A> listKOf(vararg elements: A): ListK<A> = listOf(*elements).toListK()

fun <A> List<A>.toListK(): ListK<A> = ListK(this)

object ListMonad : Monad<ListK<*>> {
  override fun <A> pure(a: A) = listKOf(a)

  override fun <A, B> K<ListK<*>, A>.bind(f: (A) -> K<ListK<*>, B>): K<ListK<*>, B> = flatMap(f).toListK()
}

data class PairK<A, B>(val first: A, val second: B) : K2<PairK<*, *>, A, B>

infix fun <A, B> A.toK(that: B): PairK<A, B> = PairK(this, that)

class PairFunctor<L> : Functor<K<PairK<*, *>, L>> {
  override fun <A, B> K2<PairK<*, *>, L, A>.fmap(f: (A) -> B): K2<PairK<*, *>, L, B> {
    val (l, a) = this
    return l toK f(a)
  }
}

data class Composed<F, G, A>(val value: K<F, K<G, A>>) : K3<Composed<*, *, *>, F, G, A>
typealias Compose<F, G> = K2<Composed<*, *, *>, F, G>

context(_: Functor<F>, _: Functor<G>)
fun <F, G> composeFunctors() = object : Functor<Compose<F, G>> {
  override fun <A, B> K<Compose<F, G>, A>.fmap(f: (A) -> B): K<Compose<F, G>, B> =
    // KT-81302
    value.fmap { it.fmap<G, _, B>(f) }.let(::Composed)
}

data class Reader<R, A>(val run: (R) -> A) : K2<Reader<*, *>, R, A>

data class Const<C, A>(val value: C) : K2<Const<*, *>, C, A>

data class Identity<A>(val value: A) : K<Identity<*>, A>

infix fun <A, B, C> ((A) -> B).compose(g: (B) -> C): (A) -> C = { a: A -> g(this(a)) }

class ReaderMonad<R> : Monad<K<Reader<*, *>, R>> {
  override fun <A, B> K2<Reader<*, *>, R, A>.fmap(f: (A) -> B): K2<Reader<*, *>, R, B> = Reader(run compose f)

  override fun <A> pure(a: A) = Reader { _: R -> a }
  override fun <A, B> K2<Reader<*, *>, R, A>.bind(f: (A) -> K2<Reader<*, *>, R, B>): K2<Reader<*, *>, R, B> = Reader { r -> f(run(r)).run(r) }
}

class ConstFunctor<C> : Functor<K<Const<*, *>, C>> {
  override fun <A, B> K2<Const<*, *>, C, A>.fmap(f: (A) -> B): K2<Const<*, *>, C, B> = Const(value)
}

object UnitMonad : Monad<K<Const<*, *>, Unit>> {
  override fun <A> pure(a: A) = Const<_, A>(Unit)

  override fun <A, B> K2<Const<*, *>, Unit, A>.bind(f: (A) -> K2<Const<*, *>, Unit, B>): K2<Const<*, *>, Unit, B> =
    Const(Unit)
}

object IdentityFunctor : Functor<Identity<*>> {
  override fun <A, B> K<Identity<*>, A>.fmap(f: (A) -> B): K<Identity<*>, B> = Identity(f(value))
}

interface BiFunctor<F> {
  fun <A, B, C, D> K2<F, A, B>.bimap(f: (A) -> C, g: (B) -> D): K2<F, C, D> = leftMap(f).rightMap(g)
  fun <A, B, C> K2<F, A, B>.leftMap(f: (A) -> C): K2<F, C, B> = bimap(f) { it }
  fun <A, B, D> K2<F, A, B>.rightMap(g: (B) -> D): K2<F, A, D> = bimap({ it }, g)
}

context(b: BiFunctor<F>)
fun <A, B, C, D, F> K2<F, A, B>.bimap(f: (A) -> C, g: (B) -> D) = with(b) { bimap(f, g) }

context(b: BiFunctor<F>)
fun <A, B, C, F> K2<F, A, B>.leftMap(f: (A) -> C) = with(b) { leftMap(f) }

context(b: BiFunctor<F>)
fun <A, B, D, F> K2<F, A, B>.rightMap(g: (B) -> D) = with(b) { rightMap(g) }

context(_: BiFunctor<F>)
fun <F, A> rightFunctor() = object : Functor<K<F, A>> {
  override fun <B, C> K2<F, A, B>.fmap(f: (B) -> C): K2<F, A, C> = rightMap(f)
}

data class Swapped<F, B, A>(val value: K2<F, A, B>) : K2<Swap<F>, B, A>
typealias Swap<F> = K<Swapped<*, *, *>, F>

context(_: BiFunctor<F>)
fun <F, A> leftFunctor() = object : Functor<K<Swap<F>, A>> {
  override fun <B, C> K2<Swap<F>, A, B>.fmap(f: (B) -> C): K2<Swap<F>, A, C> = value.leftMap(f).let(::Swapped)
}

sealed class Either<A, B>: K2<Either<*, *>, A, B>
data class Left<A, B>(val value: A) : Either<A, B>()
data class Right<A, B>(val value: B) : Either<A, B>()

object EitherBiFunctor : BiFunctor<Either<*, *>> {
  override fun <A, B, C, D> K2<Either<*, *>, A, B>.bimap(f: (A) -> C, g: (B) -> D): K2<Either<*, *>, C, D> = when (this) {
    is Left -> Left(f(value))
    is Right -> Right(g(value))
  }
}

object PairBiFunctor : BiFunctor<PairK<*, *>> {
  override fun <A, B, C, D> K2<PairK<*, *>, A, B>.bimap(f: (A) -> C, g: (B) -> D): K2<PairK<*, *>, C, D> {
    val (a, b) = this
    return f(a) toK g(b)
  }
}

data class BiComposed<Bi, F, G, A, B>(val value: K2<Bi, K<F, A>, K<G, B>>) : K2<BiCompose<Bi, F, G>, A, B>
typealias BiCompose<Bi, F, G> = K3<BiComposed<*, *, *, *, *>, Bi, F, G>

context(_: BiFunctor<BF>, _: Functor<F>, _: Functor<G>)
fun <BF, F, G> composeBiFunctors() = object : BiFunctor<BiCompose<BF, F, G>> {
  override fun <A, B, C, D> K2<BiCompose<BF, F, G>, A, B>.bimap(f: (A) -> C, g: (B) -> D): K2<BiCompose<BF, F, G>, C, D> =
    value.bimap({ it.fmap(f) }) { it.fmap(g) }.let(::BiComposed)
}

typealias Maybe<A> = BiComposed<Either<*, *>, K<Const<*, *>, Unit>, Identity<*>, Unit, A>
typealias MaybeK = K<BiCompose<Either<*, *>, K<Const<*, *>, Unit>, Identity<*>>, Unit>
val maybeFunctor: Functor<MaybeK> = context(EitherBiFunctor, ConstFunctor<Unit>(), IdentityFunctor) {
  context(composeBiFunctors<Either<*, *>, K<Const<*, *>, Unit>, Identity<*>>()) {
    rightFunctor<_, Unit>()
  }
}

interface NT<F, G>: K2<NT<*, *>, F, G> {
  operator fun <A> invoke(fa: K<F, A>): K<G, A>
}

infix fun <F, G, H> NT<F, G>.vertical(other: NT<G, H>) = object : NT<F, H> {
  override fun <A> invoke(fa: K<F, A>): K<H, A> = other(this@vertical(fa))
}

context(_: Functor<I>)
infix fun <F, G, I, J> NT<F, G>.horizontalLeft(other: NT<I, J>) = object : NT<Compose<I, F>, Compose<J, G>> {
  override fun <A> invoke(fa: K<Compose<I, F>, A>): K<Compose<J, G>, A> = other(fa.value.fmap { this@horizontalLeft(it) }).let(::Composed)
}

context(_: Functor<J>)
infix fun <F, G, I, J> NT<F, G>.horizontalRight(other: NT<I, J>) = object : NT<Compose<I, F>, Compose<J, G>> {
  override fun <A> invoke(fa: K<Compose<I, F>, A>): K<Compose<J, G>, A> =
    other(fa.value).fmap { this@horizontalRight(it) }.let(::Composed)
}

// ----------------------------------------------------------------

private fun listExample() = context(ListMonad) {
  val result: ListK<String> = listKOf("Hello", "World").fmap { "$it!" }
  if (result != listKOf("Hello!", "World!")) error("$result")
}

private fun pairExample() = context(PairFunctor<Int>()) {
  val result: PairK<Int, String> = (1 toK "Hello").fmap { "$it!" }
  if (result != (1 toK "Hello!")) error("$result")
}

private fun maybeExample() = with(maybeFunctor) {
  val a: Maybe<Int> = Maybe(Right(Identity(10)))
  val b: Maybe<String> = a.fmap { it.toString() }
  val expected: Maybe<String> = Maybe(Right(Identity("10")))
  if (b != expected) error("$b")
}

fun box(): String {
  listExample()
  pairExample()
  maybeExample()
  return "OK"
}
