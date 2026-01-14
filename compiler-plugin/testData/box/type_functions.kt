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

@TypeFunction
interface Composition<F, G, A> : K<F, K<G, A>>
typealias Compose<F, G> = K2<Composition<*, *, *>, F, G>

context(ff: Functor<F>, gg: Functor<G>)
fun <F, G> composeFunctors() = object : Functor<Compose<F, G>> {
  override fun <A, B> K<F, K<G, A>>.fmap(f: (A) -> B) = context(ff, gg) { // KT-81441
    this@fmap.fmap<F, _, _> { it.fmap(f) }
  }
}

data class Reader<R, A>(val run: (R) -> A)

@TypeFunction
interface Constant<C, A> : Id<C>

typealias Const<C> = K<Constant<*, *>, C>

infix fun <A, B, C> ((A) -> B).compose(g: (B) -> C) = { a: A -> g(this(a)) }

class ReaderMonad<R> : Monad<K<Reader<*, *>, R>> {
  override fun <A, B> Reader<R, A>.fmap(f: (A) -> B) = Reader(run compose f)

  override fun <A> pure(a: A) = Reader { _: R -> a }
  override fun <A, B> Reader<R, A>.bind(f: (A) -> Reader<R, B>) = Reader { r: R -> f(run(r)).run(r) }
}

class ConstFunctor<C> : Functor<Const<C>> {
  override fun <A, B> C.fmap(f: (A) -> B) = this
}

object UnitMonad : Monad<Const<Unit>> {
  override fun <A> pure(a: A) {}

  override fun <A, B> Unit.bind(f: (A) -> Unit) {}
}

object IdentityFunctor : Functor<Identity> {
  override fun <A, B> A.fmap(f: (A) -> B) = f(this)
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

@TypeFunction
interface Swapped<F, A, B> : K2<F, B, A>
typealias Swap<F> = K<Swapped<*, *, *>, F>

context(_: BiFunctor<F>)
fun <F, A> leftFunctor() = object : Functor<K<Swap<F>, A>> {
  override fun <B, C> K2<F, B, A>.fmap(f: (B) -> C) = leftMap(f)
}

sealed class Either<A, B>
data class Left<A, B>(val value: A) : Either<A, B>()
data class Right<A, B>(val value: B) : Either<A, B>()

object EitherBiFunctor : BiFunctor<Either<*, *>> {
  override fun <A, B, C, D> Either<A, B>.bimap(f: (A) -> C, g: (B) -> D): Either<C, D> = when (this) {
    is Left<A, B> -> Left(f(value))
    is Right<A, B> -> Right(g(value))
  }
}

object PairBiFunctor : BiFunctor<Pair<*, *>> {
  override fun <A, B, C, D> Pair<A, B>.bimap(f: (A) -> C, g: (B) -> D): Pair<C, D> {
    val (a, b) = this
    return f(a) to g(b)
  }
}

@TypeFunction
interface BiComposed<Bi, F, G, A, B> : K2<Bi, K<F, A>, K<G, B>>
typealias BiCompose<Bi, F, G> = K3<BiComposed<*, *, *, *, *>, Bi, F, G>

context(bf: BiFunctor<BF>, _: Functor<F>, _: Functor<G>)
fun <BF, F, G> composeBiFunctors() = object : BiFunctor<BiCompose<BF, F, G>> {
  override fun <A, B, C, D> K2<BF, K<F, A>, K<G, B>>.bimap(
    f: (A) -> C,
    g: (B) -> D
  ) = context(bf) { this@bimap.bimap<_, _, _, _, BF>({ it.fmap(f) }) { it.fmap(g) } } // KT-81441
}

typealias Maybe<A> = K<MaybeK, A>
typealias MaybeK = K<BiCompose<Either<*, *>, Const<Unit>, Identity>, Unit>

val maybeFunctor: Functor<MaybeK> = context(EitherBiFunctor, ConstFunctor<Unit>(), IdentityFunctor) {
  context(composeBiFunctors<Either<*, *>, Const<Unit>, Identity>()) {
    rightFunctor<BiCompose<Either<*, *>, Const<Unit>, Identity>, Unit>()
  }
}

@TypeFunction
interface BiComposed2<Bi, F, G, A> : Compose<K<Bi, K<F, A>>, G>
typealias BiCompose2<Bi, F, G> = K3<BiComposed2<*, *, *, *>, Bi, F, G>

context(bf: BiFunctor<BF>, _: Functor<F>, _: Functor<G>)
fun <BF, F, G> composeBiFunctors2() = object : BiFunctor<BiCompose2<BF, F, G>> {
  override fun <A, B, C, D> K2<BF, K<F, A>, K<G, B>>.bimap(
    f: (A) -> C,
    g: (B) -> D
  ) = context(bf) { this@bimap.bimap<_, _, _, _, BF>({ it.fmap(f) }) { it.fmap(g) } } // KT-81441
}

typealias Maybe2<A> = K<Maybe2K, A>
typealias Maybe2K = Compose<K<Either<*, *>, Unit>, Identity>

val maybeFunctor2: Functor<Maybe2K> = context(EitherBiFunctor, ConstFunctor<Unit>(), IdentityFunctor) {
  context(composeBiFunctors2<Either<*, *>, Const<Unit>, Identity>()) {
    rightFunctor<BiCompose2<Either<*, *>, Const<Unit>, Identity>, Unit>()
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
  override fun <A> invoke(fa: K<I, K<F, A>>) = other(fa.fmap { this@horizontalLeft(it) })
}

context(_: Functor<J>)
infix fun <F, G, I, J> NT<F, G>.horizontalRight(other: NT<I, J>) = object : NT<Compose<I, F>, Compose<J, G>> {
  override fun <A> invoke(fa: K<I, K<F, A>>) = other(fa).fmap { this@horizontalRight(it) }
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
  val a: Maybe<Int> = Right(10)
  val b = a.fmap { it.toString() }.fmap { "$it!" } // look ma! no expandTo needed
  val expected: Either<Unit, String> = Right("10!")
  if (b != expected) error("$b")
}

private fun maybeExample2() = context(maybeFunctor2) {
  val a: Either<Unit, Int> = Right(10)
  val b = a.expandTo<Maybe2<Int>>().fmap { it.toString() } // intentionally showcasing expandTo
  val expected: Either<Unit, String> = Right("10")
  if (b != expected) error("$b")
}

fun box(): String {
  listExample()
  pairExample()
  maybeExample()
  maybeExample2()
  return "OK"
}
