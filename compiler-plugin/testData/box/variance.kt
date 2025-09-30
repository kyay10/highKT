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

class ListK<out A>(list: List<A>) : KOut<ListK<*>, A>, List<A> by list, AbstractList<A>()

fun <A> listKOf(vararg elements: A): ListK<A> = listOf(*elements).toListK()

fun <A> List<A>.toListK(): ListK<A> = ListK(this)

object ListMonad : Monad<ListK<*>> {
  override fun <A> pure(a: A) = listKOf(a)

  override fun <A, B> K<ListK<*>, A>.bind(f: (A) -> K<ListK<*>, B>): K<ListK<*>, B> {
    fix().all = 42
    return flatMap(f).toListK()
  }
}

data class PairK<out A, out B>(val first: A, val second: B) : KBi<PairK<*, *>, A, B>

infix fun <A, B> A.toK(that: B): PairK<A, B> = PairK(this, that)

class PairFunctor<L> : Functor<K<PairK<*, *>, L>> {
  override fun <A, B> K2<PairK<*, *>, L, A>.fmap(f: (A) -> B): K2<PairK<*, *>, L, B> {
    fix().all = 42
    val (l, a) = this
    return l toK f(a)
  }
}

data class Composed<out F, out G, A>(val value: K<F, K<@UnsafeVariance G, A>>) : K<Compose<F, G>, A>
typealias Compose<F, G> = KBi<Composed<*, *, *>, F, G>

context(_: Functor<F>, _: Functor<G>)
fun <F, G> composeFunctors() = object : Functor<Compose<F, G>> {
  override fun <A, B> K<Compose<F, G>, A>.fmap(f: (A) -> B): K<Compose<F, G>, B> {
    fix().all = 42
    // KT-81302
    val foo = value.fmap<F, K<G, A>, K<G, B>> {
      val bar = it.fmap<G, A, B>(f)
      bar
    }
    return Composed<F, G, B>(foo)
  }
}

data class Reader<in R, out A>(val run: (R) -> A) : KPro<Reader<*, *>, R, A>

data class Const<out C, out A>(val value: C) : KBi<Const<*, *>, C, A>

data class Identity<out A>(val value: A) : KOut<Identity<*>, A>

infix fun <A, B, C> ((A) -> B).compose(g: (B) -> C): (A) -> C = { a: A -> g(this(a)) }

class ReaderMonad<R> : Monad<K<Reader<*, *>, R>> {
  override fun <A, B> K2<Reader<*, *>, R, A>.fmap(f: (A) -> B): K2<Reader<*, *>, R, B> {
    fix().all = 42
    return Reader(run compose f)
  }

  override fun <A> pure(a: A) = Reader { _: R -> a }
  override fun <A, B> K2<Reader<*, *>, R, A>.bind(f: (A) -> K2<Reader<*, *>, R, B>): K2<Reader<*, *>, R, B> {
    fix().all = 42
    return Reader { r -> f(run(r)).run(r) }
  }
}

class ConstFunctor<C> : Functor<KOut<Const<*, *>, C>> {
  override fun <A, B> K<KOut<Const<*, *>, C>, A>.fmap(f: (A) -> B): K<KOut<Const<*, *>, C>, B> {
    fix().all = 42
    return Const(value)
  }
}

object UnitMonad : Monad<K<Const<*, *>, Unit>> {
  override fun <A> pure(a: A) = Const<_, A>(Unit)

  override fun <A, B> K2<Const<*, *>, Unit, A>.bind(f: (A) -> K2<Const<*, *>, Unit, B>): K2<Const<*, *>, Unit, B> {
    fix().all = 42
    return Const(Unit)
  }
}

object IdentityFunctor : Functor<Identity<*>> {
  override fun <A, B> K<Identity<*>, A>.fmap(f: (A) -> B): K<Identity<*>, B> {
    fix().all = 42
    return Identity(f(value))
  }
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

data class Swapped<out F, B, A>(val value: K2<F, A, B>) : KBi<Swap<F>, B, A>
typealias Swap<F> = KOut<Swapped<*, *, *>, F>

context(_: BiFunctor<F>)
fun <F, A> leftFunctor() = object : Functor<K<Swap<F>, A>> {
  override fun <B, C> K2<Swap<F>, A, B>.fmap(f: (B) -> C): K2<Swap<F>, A, C> {
    fix().all = 42
    return value.leftMap(f).let(::Swapped)
  }
}

sealed class Either<out A, out B> : KBi<Either<*, *>, A, B>
data class Left<out A>(val value: A) : Either<A, Nothing>()
data class Right<out B>(val value: B) : Either<Nothing, B>()

object EitherBiFunctor : BiFunctor<Either<*, *>> {
  override fun <A, B, C, D> K2<Either<*, *>, A, B>.bimap(f: (A) -> C, g: (B) -> D): K2<Either<*, *>, C, D> {
    fix().all = 42
    return when (this) {
      is Left<A> -> Left(f(value))
      is Right<B> -> Right(g(value))
    }
  }
}

object PairBiFunctor : BiFunctor<PairK<*, *>> {
  override fun <A, B, C, D> K2<PairK<*, *>, A, B>.bimap(f: (A) -> C, g: (B) -> D): K2<PairK<*, *>, C, D> {
    fix().all = 42
    val (a, b) = this
    return f(a) toK g(b)
  }
}

data class BiComposed<out BI, out F, out G, A, B>(val value: K2<BI, K<@UnsafeVariance F, A>, K<@UnsafeVariance G, B>>) :
  KBi<BiCompose<BI, F, G>, A, B>
typealias BiCompose<BI, F, G> = KBi<KOut<BiComposed<*, *, *, *, *>, BI>, F, G>

context(_: BiFunctor<BF>, _: Functor<F>, _: Functor<G>)
fun <BF, F, G> composeBiFunctors() = object : BiFunctor<BiCompose<BF, F, G>> {
  override fun <A, B, C, D> K2<BiCompose<BF, F, G>, A, B>.bimap(
    f: (A) -> C,
    g: (B) -> D
  ): K2<BiCompose<BF, F, G>, C, D> {
    fix().all = 42
    return value.bimap({ it.fmap(f) }) { it.fmap(g) }.let(::BiComposed)
  }
}

typealias Maybe<A> = BiComposed<Either<*, *>, KOut<Const<*, *>, Unit>, Identity<*>, Unit, A>
typealias MaybeK = K<BiCompose<Either<*, *>, KOut<Const<*, *>, Unit>, Identity<*>>, Unit>

fun <A> A.just(): Maybe<A> {
  val either: Either<Const<Unit, Unit>, Identity<A>> = Right(Identity(this))
  return BiComposed(either)
}

val nothing: Maybe<Nothing> = BiComposed<Either<*, *>, KOut<Const<*, *>, Unit>, Identity<*>, Unit, Nothing>(Const<Unit, Unit>(Unit))

val maybeFunctor: Functor<MaybeK> = context(EitherBiFunctor, ConstFunctor<Unit>(), IdentityFunctor) {
  context(composeBiFunctors<Either<*, *>, KOut<Const<*, *>, Unit>, Identity<*>>()) {
    rightFunctor<_, Unit>()
  }
}

interface NT<in F, out G> : KPro<NT<*, *>, F, G> {
  operator fun <A> invoke(fa: K<F, A>): K<G, A>
}

infix fun <F, G, H> NT<F, G>.vertical(other: NT<G, H>) = object : NT<F, H> {
  override fun <A> invoke(fa: K<F, A>): K<H, A> = other(this@vertical(fa))
}

context(_: Functor<I>)
infix fun <F, G, I, J> NT<F, G>.horizontalLeft(other: NT<I, J>) = object : NT<Compose<I, F>, Compose<J, G>> {
  override fun <A> invoke(fa: K<Compose<I, F>, A>): K<Compose<J, G>, A> {
    fix().all = 42
    val foo = other(fa.value.fmap<I, K<F, A>, K<G, A>> { this@horizontalLeft(it) })
    return Composed<J, G, A>(foo)
  }
}

context(_: Functor<J>)
infix fun <F, G, I, J> NT<F, G>.horizontalRight(other: NT<I, J>) = object : NT<Compose<I, F>, Compose<J, G>> {
  override fun <A> invoke(fa: K<Compose<I, F>, A>): K<Compose<J, G>, A> {
    fix().all = 42
    val foo = other(fa.value).fmap<J, K<F, A>, K<G, A>> { this@horizontalRight(it) }
    return Composed<J, G, A>(foo)
  }
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
  val a = Right(Identity(10)).just()
  val b = a.fmap<MaybeK, Int, String> { it.toString() }
  val expected = Right(Identity("10")).just()
  if (b != expected) error("$b")
}

fun box(): String {
  listExample()
  pairExample()
  maybeExample()
  return "OK"
}
