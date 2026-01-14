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
fun <F, A, B> K<F, A>.fmap(f: (A) -> B) = with(functor) { this@fmap.fmap(f) }

context(monad: Monad<M>)
fun <M, A> pure(a: A) = with(monad) { pure(a) }

context(monad: Monad<M>)
fun <M, A, B> K<M, A>.bind(f: (A) -> K<M, B>) = with(monad) { this@bind.bind(f) }

object ListMonad : Monad<List<*>> {
  override fun <A> pure(a: A) = listOf(a)

  override fun <A, B> List<A>.bind(f: (A) -> List<B>) = flatMap(f)
}

class PairFunctor<L> : Functor<K<Pair<*, *>, L>> {
  override fun <A, B> Pair<L, A>.fmap(f: (A) -> B): Pair<L, B> {
    val (l, a) = this
    return l to f(a)
  }
}

data class Composed<F, G, A>(val value: K<F, K<G, A>>)
typealias Compose<F, G> = K2<Composed<*, *, *>, F, G>

context(ff: Functor<F>, gg: Functor<G>)
fun <F, G> composeFunctors() = object : Functor<Compose<F, G>> {
  override fun <A, B> Composed<F, G, A>.fmap(f: (A) -> B): Composed<F, G, B> = context(ff, gg) { // KT-81441
    Composed(value.fmap { it.fmap(f) })
  }
}

data class Reader<R, A>(val run: (R) -> A)

data class Const<C, A>(val value: C)

data class Identity<A>(val value: A)

infix fun <A, B, C> ((A) -> B).compose(g: (B) -> C) = { a: A -> g(this(a)) }

class ReaderMonad<R> : Monad<K<Reader<*, *>, R>> {
  override fun <A, B> Reader<R, A>.fmap(f: (A) -> B) = Reader(run compose f)

  override fun <A> pure(a: A) = Reader { _: R -> a }
  override fun <A, B> Reader<R, A>.bind(f: (A) -> Reader<R, B>) = Reader { r: R -> f(run(r)).run(r) }
}

class ConstFunctor<C> : Functor<K<Const<*, *>, C>> {
  override fun <A, B> Const<C, A>.fmap(f: (A) -> B) = Const<_, B>(value)
}

object UnitMonad : Monad<K<Const<*, *>, Unit>> {
  override fun <A> pure(a: A) = Const<_, A>(Unit)

  override fun <A, B> Const<Unit, A>.bind(f: (A) -> Const<Unit, B>) = Const<_, B>(Unit)
}

object IdentityFunctor : Functor<Identity<*>> {
  override fun <A, B> Identity<A>.fmap(f: (A) -> B) = Identity(f(value))
}

interface BiFunctor<F> {
  fun <A, B, C, D> K2<F, A, B>.bimap(f: (A) -> C, g: (B) -> D): K2<F, C, D> = leftMap(f).rightMap(g)
  fun <A, B, C> K2<F, A, B>.leftMap(f: (A) -> C): K2<F, C, B> = bimap(f) { it }
  fun <A, B, D> K2<F, A, B>.rightMap(g: (B) -> D): K2<F, A, D> = bimap({ it }, g)
}

context(b: BiFunctor<F>)
fun <A, B, C, D, F> K2<F, A, B>.bimap(f: (A) -> C, g: (B) -> D) = with(b) { this@bimap.bimap(f, g) }

context(b: BiFunctor<F>)
fun <A, B, C, F> K2<F, A, B>.leftMap(f: (A) -> C) = with(b) { this@leftMap.leftMap(f) }

context(b: BiFunctor<F>)
fun <A, B, D, F> K2<F, A, B>.rightMap(g: (B) -> D) = with(b) { this@rightMap.rightMap(g) }

context(_: BiFunctor<F>)
fun <F, A> rightFunctor() = object : Functor<K<F, A>> {
  override fun <B, C> K2<F, A, B>.fmap(f: (B) -> C) = rightMap(f)
}

data class Swapped<F, B, A>(val value: K2<F, A, B>) : K2<Swap<F>, B, A>
typealias Swap<F> = K<Swapped<*, *, *>, F>

context(_: BiFunctor<F>)
fun <F, A> leftFunctor() = object : Functor<K<Swap<F>, A>> {
  override fun <B, C> Swapped<F, A, B>.fmap(f: (B) -> C) = value.leftMap(f).let(::Swapped)
}

sealed class Either<A, B>
data class Left<A, B>(val value: A) : Either<A, B>()
data class Right<A, B>(val value: B) : Either<A, B>()

object EitherBiFunctor : BiFunctor<Either<*, *>> {
  override fun <A, B, C, D> Either<A, B>.bimap(f: (A) -> C, g: (B) -> D): Either<C, D> = when (this) {
    is Left -> Left(f(value))
    is Right -> Right(g(value))
  }
}

object PairBiFunctor : BiFunctor<Pair<*, *>> {
  override fun <A, B, C, D> Pair<A, B>.bimap(f: (A) -> C, g: (B) -> D): Pair<C, D> {
    val (a, b) = this
    return f(a) to g(b)
  }
}

data class BiComposed<Bi, F, G, A, B>(val value: K2<Bi, K<F, A>, K<G, B>>)
typealias BiCompose<Bi, F, G> = K3<BiComposed<*, *, *, *, *>, Bi, F, G>

context(_: BiFunctor<BF>, _: Functor<F>, _: Functor<G>)
fun <BF, F, G> composeBiFunctors() = object : BiFunctor<BiCompose<BF, F, G>> {
  override fun <A, B, C, D> BiComposed<BF, F, G, A, B>.bimap(
    f: (A) -> C,
    g: (B) -> D
  ) = value.bimap({ it.fmap(f) }) { it.fmap(g) }.let(::BiComposed)
}

typealias Maybe<A> = BiComposed<Either<*, *>, K<Const<*, *>, Unit>, Identity<*>, Unit, A>
typealias MaybeK = K<BiCompose<Either<*, *>, K<Const<*, *>, Unit>, Identity<*>>, Unit>

val maybeFunctor: Functor<MaybeK> = context(EitherBiFunctor, ConstFunctor<Unit>(), IdentityFunctor) {
  context(composeBiFunctors<Either<*, *>, K<Const<*, *>, Unit>, Identity<*>>()) {
    rightFunctor<_, Unit>()
  }
}

interface NT<F, G> {
  operator fun <A> invoke(fa: K<F, A>): K<G, A>
}

infix fun <F, G, H> NT<F, G>.vertical(other: NT<G, H>) = object : NT<F, H> {
  override fun <A> invoke(fa: K<F, A>) = other(this@vertical(fa))
}

context(_: Functor<I>)
infix fun <F, G, I, J> NT<F, G>.horizontalLeft(other: NT<I, J>) = object : NT<Compose<I, F>, Compose<J, G>> {
  override fun <A> invoke(fa: Composed<I, F, A>) = other(fa.value.fmap { this@horizontalLeft(it) }).let(::Composed)
}

context(_: Functor<J>)
infix fun <F, G, I, J> NT<F, G>.horizontalRight(other: NT<I, J>) = object : NT<Compose<I, F>, Compose<J, G>> {
  override fun <A> invoke(fa: Composed<I, F, A>) = other(fa.value).fmap { this@horizontalRight(it) }.let(::Composed)
}

// ----------------------------------------------------------------

private fun listExample() = context(ListMonad) {
  val result: List<String> = listOf("Hello", "World").fmap { "$it!" }
  if (result != listOf("Hello!", "World!")) error("$result")
}

private fun pairExample() = context(PairFunctor<Int>()) {
  val result: Pair<Int, String> = (1 to "Hello").fmap { "$it!" }
  if (result != (1 to "Hello!")) error("$result")
}

private fun maybeExample() = context(maybeFunctor) {
  val aMaybe = Maybe(Right(Identity(10)))
  val b: Maybe<String> = aMaybe.fmap { it.toString() }
  val expected: Maybe<String> = Maybe(Right(Identity("10")))
  if (b != expected) error("$b")
}

fun box(): String {
  listExample()
  pairExample()
  maybeExample()
  return "OK"
}
