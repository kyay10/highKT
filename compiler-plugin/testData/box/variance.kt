package foo.bar

// LANGUAGE: +ContextParameters
// LANGUAGE_VERSION: 2.3
// ALLOW_DANGEROUS_LANGUAGE_VERSION_TESTING

import io.github.kyay10.highkt.*

interface Functor<F> {
  fun <A, B> Out<F, A>.fmap(f: (A) -> B): Out<F, B>
}

interface Monad<M> : Functor<M> {
  fun <A> pure(a: A): Out<M, A>

  fun <A, B> Out<M, A>.bind(f: (A) -> Out<M, B>): Out<M, B>

  override fun <A, B> Out<M, A>.fmap(f: (A) -> B) = bind { a -> pure(f(a)) }
}

context(functor: Functor<F>)
fun <F, A, B> Out<F, A>.fmap(f: (A) -> B) = with(functor) { fmap(f) }

context(monad: Monad<M>)
fun <M, A> pure(a: A) = with(monad) { pure(a) }

context(monad: Monad<M>)
fun <M, A, B> Out<M, A>.bind(f: (A) -> Out<M, B>) = with(monad) { bind(f) }

object ListMonad : Monad<List<*>> {
  override fun <A> pure(a: A): Out<List<*>, A> = listOf(a)

  override fun <A, B> Out<List<*>, A>.bind(f: (A) -> Out<List<*>, B>): Out<List<*>, B> = flatMap(f)
}

class PairFunctor<L> : Functor<Out<Pair<*, *>, L>> {
  override fun <A, B> Bi<Pair<*, *>, L, A>.fmap(f: (A) -> B): Bi<Pair<*, *>, L, B> {
    val (l, a) = this
    return l to f(a)
  }
}

data class Composed<out F, out G, out A>(val value: Out<F, Out<G, A>>)
typealias Compose<F, G> = Bi<Composed<*, *, *>, F, G>

context(ff: Functor<F>, gg: Functor<G>)
fun <F, G> composeFunctors() = object : Functor<Compose<F, G>> {
  override fun <A, B> Out<Compose<F, G>, A>.fmap(f: (A) -> B): Out<Compose<F, G>, B> = context(ff, gg) { // KT-81441
    Composed(value.fmap { it.fmap(f) })
  }
}

data class Reader<in R, out A>(val run: (R) -> A)

data class Const<out C, out A>(val value: C)

data class Identity<out A>(val value: A)

infix fun <A, B, C> ((A) -> B).compose(g: (B) -> C): (A) -> C = { a: A -> g(this(a)) }

class ReaderMonad<R> : Monad<In<Reader<*, *>, R>> {
  override fun <A, B> Pro<Reader<*, *>, R, A>.fmap(f: (A) -> B): Pro<Reader<*, *>, R, B> = Reader(run compose f)

  override fun <A> pure(a: A): Pro<Reader<*, *>, R, A> = Reader { _: R -> a }
  override fun <A, B> Pro<Reader<*, *>, R, A>.bind(f: (A) -> Pro<Reader<*, *>, R, B>): Pro<Reader<*, *>, R, B> =
    Reader { r: R -> f(run(r)).run(r) }
}

class ConstFunctor<C> : Functor<Out<Const<*, *>, C>> {
  override fun <A, B> Bi<Const<*, *>, C, A>.fmap(f: (A) -> B): Bi<Const<*, *>, C, B> = Const<_, B>(value)
}

object UnitMonad : Monad<Out<Const<*, *>, Unit>> {
  override fun <A> pure(a: A): Bi<Const<*, *>, Unit, A> = Const<_, A>(Unit)

  override fun <A, B> Bi<Const<*, *>, Unit, A>.bind(f: (A) -> Bi<Const<*, *>, Unit, B>): Bi<Const<*, *>, Unit, B> =
    Const<_, B>(Unit)
}

object IdentityFunctor : Functor<Identity<*>> {
  override fun <A, B> Out<Identity<*>, A>.fmap(f: (A) -> B): Out<Identity<*>, B> = Identity(f(value))
}

interface BiFunctor<F> {
  fun <A, B, C, D> Bi<F, A, B>.bimap(f: (A) -> C, g: (B) -> D): Bi<F, C, D> = leftMap(f).rightMap(g)
  fun <A, B, C> Bi<F, A, B>.leftMap(f: (A) -> C): Bi<F, C, B> = bimap(f) { it }
  fun <A, B, D> Bi<F, A, B>.rightMap(g: (B) -> D): Bi<F, A, D> = bimap({ it }, g)
}

context(b: BiFunctor<F>)
fun <A, B, C, D, F> Bi<F, A, B>.bimap(f: (A) -> C, g: (B) -> D) = with(b) { bimap(f, g) }

context(b: BiFunctor<F>)
fun <A, B, C, F> Bi<F, A, B>.leftMap(f: (A) -> C) = with(b) { leftMap(f) }

context(b: BiFunctor<F>)
fun <A, B, D, F> Bi<F, A, B>.rightMap(g: (B) -> D) = with(b) { rightMap(g) }

context(_: BiFunctor<F>)
fun <F, A> rightFunctor() = object : Functor<Out<F, A>> {
  override fun <B, C> Bi<F, A, B>.fmap(f: (B) -> C): Bi<F, A, C> = rightMap(f)
}

data class Swapped<out F, out B, out A>(val value: Bi<F, A, B>)
typealias Swap<F> = Out<Swapped<*, *, *>, F>

context(_: BiFunctor<F>)
fun <F, A> leftFunctor() = object : Functor<Out<Swap<F>, A>> {
  override fun <B, C> Bi<Swap<F>, A, B>.fmap(f: (B) -> C): Bi<Swap<F>, A, C> = value.leftMap(f).let(::Swapped)
}

sealed class Either<out A, out B>
data class Left<out A>(val value: A) : Either<A, Nothing>()
data class Right<out B>(val value: B) : Either<Nothing, B>()

fun <A> A.right(): Either<Nothing, A> = Right(this)
fun <A> A.left(): Either<A, Nothing> = Left(this)

object EitherBiFunctor : BiFunctor<Either<*, *>> {
  override fun <A, B, C, D> Bi<Either<*, *>, A, B>.bimap(f: (A) -> C, g: (B) -> D): Bi<Either<*, *>, C, D> =
    when (this) {
      is Left<A> -> f(value).left()
      is Right<B> -> g(value).right()
    }
}

object PairBiFunctor : BiFunctor<Pair<*, *>> {
  override fun <A, B, C, D> Bi<Pair<*, *>, A, B>.bimap(f: (A) -> C, g: (B) -> D): Bi<Pair<*, *>, C, D> {
    val (a, b) = this
    return f(a) to g(b)
  }
}

data class BiComposed<out BI, out F, out G, out A, out B>(val value: Bi<BI, Out<F, A>, Out<G, B>>)
typealias BiCompose<BI, F, G> = Bi<Out<BiComposed<*, *, *, *, *>, BI>, F, G>

context(_: BiFunctor<BF>, _: Functor<F>, _: Functor<G>)
fun <BF, F, G> composeBiFunctors() = object : BiFunctor<BiCompose<BF, F, G>> {
  override fun <A, B, C, D> Bi<BiCompose<BF, F, G>, A, B>.bimap(
    f: (A) -> C,
    g: (B) -> D
  ): Bi<BiCompose<BF, F, G>, C, D> = value.bimap({ it.fmap(f) }) { it.fmap(g) }.let(::BiComposed)
}

typealias Maybe<A> = BiComposed<Either<*, *>, Out<Const<*, *>, Unit>, Identity<*>, Unit, A>
typealias MaybeK = Out<BiCompose<Either<*, *>, Out<Const<*, *>, Unit>, Identity<*>>, Unit>

val maybeFunctor: Functor<MaybeK> = context(EitherBiFunctor, ConstFunctor<Unit>(), IdentityFunctor) {
  context(composeBiFunctors<Either<*, *>, Out<Const<*, *>, Unit>, Identity<*>>()) {
    rightFunctor<_, Unit>()
  }
}

interface NT<in F, out G> {
  operator fun <A> invoke(fa: Out<F, A>): Out<G, A>
}

infix fun <F, G, H> NT<F, G>.vertical(other: NT<G, H>) = object : NT<F, H> {
  override fun <A> invoke(fa: Out<F, A>): Out<H, A> = other(this@vertical(fa))
}

context(_: Functor<I>)
infix fun <F, G, I, J> NT<F, G>.horizontalLeft(other: NT<I, J>) = object : NT<Compose<I, F>, Compose<J, G>> {
  override operator fun <A> invoke(fa: Out<Compose<I, F>, A>): Out<Compose<J, G>, A> {
    return other(fa.value.fmap { this@horizontalLeft(it) }).let(::Composed)
  }
}

context(_: Functor<J>)
infix fun <F, G, I, J> NT<F, G>.horizontalRight(other: NT<I, J>) = object : NT<Compose<I, F>, Compose<J, G>> {
  override fun <A> invoke(fa: Out<Compose<I, F>, A>): Out<Compose<J, G>, A> =
    other(fa.value).fmap { this@horizontalRight(it) }.let(::Composed)
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

private fun maybeExample() = with(maybeFunctor) {
  val a = Maybe(Identity(10).right())
  val b: Maybe<String> = a.fmap { it.toString() }
  val expected = Maybe(Identity("10").right())
  if (b != expected) error("$b")
}

fun box(): String {
  listExample()
  pairExample()
  maybeExample()
  return "OK"
}
