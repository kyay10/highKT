package foo.bar

// LANGUAGE: +ContextParameters

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

class ListK<out A>(list: List<A>) : Out<ListK<*>, A>, List<A> by list, AbstractList<A>()

fun <A> listKOf(vararg elements: A): ListK<A> = listOf(*elements).toListK()

fun <A> List<A>.toListK(): ListK<A> = ListK(this)

object ListMonad : Monad<ListK<*>> {
  override fun <A> pure(a: A) = listKOf(a)

  override fun <A, B> Out<ListK<*>, A>.bind(f: (A) -> Out<ListK<*>, B>): Out<ListK<*>, B> = flatMap(f).toListK()
}

data class PairK<out A, out B>(val first: A, val second: B) : Bi<PairK<*, *>, A, B>

infix fun <A, B> A.toK(that: B): PairK<A, B> = PairK(this, that)

class PairFunctor<L> : Functor<Out<PairK<*, *>, L>> {
  override fun <A, B> Bi<PairK<*, *>, L, A>.fmap(f: (A) -> B): Bi<PairK<*, *>, L, B> {
    val (l, a) = this
    return l toK f(a)
  }
}

data class Composed<out F, out G, out A>(val value: Out<F, Out<G, A>>) : Out<Compose<F, G>, A>
typealias Compose<F, G> = Bi<Composed<*, *, *>, F, G>

context(_: Functor<F>, _: Functor<G>)
fun <F, G> composeFunctors() = object : Functor<Compose<F, G>> {
  override fun <A, B> Out<Compose<F, G>, A>.fmap(f: (A) -> B): Out<Compose<F, G>, B> =
    // KT-81302
    value.fmap { it.fmap<G, A, B>(f) }.let(::Composed)
}

data class Reader<in R, out A>(val run: (R) -> A) : Pro<Reader<*, *>, R, A>

data class Const<out C, out A>(val value: C) : Bi<Const<*, *>, C, A>

data class Identity<out A>(val value: A) : Out<Identity<*>, A>

infix fun <A, B, C> ((A) -> B).compose(g: (B) -> C): (A) -> C = { a: A -> g(this(a)) }

class ReaderMonad<R> : Monad<In<Reader<*, *>, R>> {
  override fun <A, B> Pro<Reader<*, *>, R, A>.fmap(f: (A) -> B): Pro<Reader<*, *>, R, B> = Reader(run compose f)

  override fun <A> pure(a: A) = Reader { _: R -> a }
  override fun <A, B> Pro<Reader<*, *>, R, A>.bind(f: (A) -> Pro<Reader<*, *>, R, B>): Pro<Reader<*, *>, R, B> =
    Reader { r -> f(run(r)).run(r) }
}

class ConstFunctor<C> : Functor<Out<Const<*, *>, C>> {
  override fun <A, B> Bi<Const<*, *>, C, A>.fmap(f: (A) -> B): Bi<Const<*, *>, C, B> = Const(value)
}

object UnitMonad : Monad<Out<Const<*, *>, Unit>> {
  override fun <A> pure(a: A) = Const<_, A>(Unit)

  override fun <A, B> Bi<Const<*, *>, Unit, A>.bind(f: (A) -> Bi<Const<*, *>, Unit, B>): Bi<Const<*, *>, Unit, B> =
    Const(Unit)
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

data class Swapped<out F, out B, out A>(val value: Bi<F, A, B>) :
  Bi<Swap<F>, B, A>
typealias Swap<F> = Out<Swapped<*, *, *>, F>

context(_: BiFunctor<F>)
fun <F, A> leftFunctor() = object : Functor<Out<Swap<F>, A>> {
  override fun <B, C> Bi<Swap<F>, A, B>.fmap(f: (B) -> C): Bi<Swap<F>, A, C> = value.leftMap(f).let(::Swapped)
}

sealed class Either<out A, out B> : Bi<Either<*, *>, A, B>
data class Left<out A>(val value: A) : Either<A, Nothing>()
data class Right<out B>(val value: B) : Either<Nothing, B>()

object EitherBiFunctor : BiFunctor<Either<*, *>> {
  override fun <A, B, C, D> Bi<Either<*, *>, A, B>.bimap(f: (A) -> C, g: (B) -> D): Bi<Either<*, *>, C, D> =
    when (this) {
      is Left -> Left(f(value))
      is Right -> Right(g(value))
    }
}

object PairBiFunctor : BiFunctor<PairK<*, *>> {
  override fun <A, B, C, D> Bi<PairK<*, *>, A, B>.bimap(f: (A) -> C, g: (B) -> D): Bi<PairK<*, *>, C, D> {
    val (a, b) = this
    return f(a) toK g(b)
  }
}

data class BiComposed<out BI, out F, out G, out A, out B>(val value: Bi<BI, Out<F, A>, Out<G, B>>) :
  Bi<BiCompose<BI, F, G>, A, B>
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

interface NT<in F, out G> : Pro<NT<*, *>, F, G> {
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
  val result: ListK<String> = listKOf("Hello", "World").fmap { "$it!" }
  if (result != listKOf("Hello!", "World!")) error("$result")
}

private fun pairExample() = context(PairFunctor<Int>()) {
  val result: PairK<Int, String> = (1 toK "Hello").fmap { "$it!" }
  if (result != (1 toK "Hello!")) error("$result")
}

private fun maybeExample() = with(maybeFunctor) {
  val a = Maybe(Right(Identity(10)))
  val b: Maybe<String> = a.fmap { it.toString() }
  val expected = Maybe(Right(Identity("10")))
  if (b != expected) error("$b")
}

fun box(): String {
  listExample()
  pairExample()
  maybeExample()
  return "OK"
}
