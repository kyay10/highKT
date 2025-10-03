package foo.bar

// LANGUAGE: +ContextParameters +AllowCheckForErasedTypesInContracts

import io.github.kyay10.highkt.*
import kotlin.contracts.*

typealias Obj<Cat, A> = K2<Cat, A, A>

interface Category<Cat> {
  fun <A, B, C> K2<Cat, B, C>.compose(g: K2<Cat, A, B>): K2<Cat, A, C>
  fun <A> K2<Cat, A, *>.source(): Obj<Cat, A>
  fun <A> K2<Cat, *, A>.target(): Obj<Cat, A>
}

context(category: Category<Cat>)
infix fun <Cat, A, B, C> K2<Cat, B, C>.compose(g: K2<Cat, A, B>) = with(category) { compose(g) }

context(category: Category<Cat>)
fun <Cat, A> K2<Cat, A, *>.source() = with(category) { source<A>() }

context(category: Category<Cat>)
fun <Cat, A> K2<Cat, *, A>.target() = with(category) { target<A>() }

data class Opposite<Cat, A, B>(val value: K2<Cat, B, A>) : K2<Opp<Cat>, A, B>
typealias Opp<Arr> = K<Opposite<*, *, *>, Arr>

context(_: Category<Cat>)
fun <Cat> oppositeCategory(): Category<Opp<Cat>> = object : Category<Opp<Cat>> {
  override fun <A, B, C> K2<Opp<Cat>, B, C>.compose(g: K2<Opp<Cat>, A, B>): K2<Opp<Cat>, A, C> =
    Opposite(g.value.compose(value))

  override fun <A> K2<Opp<Cat>, A, *>.source(): Obj<Opp<Cat>, A> = Opposite(value.target())

  override fun <A> K2<Opp<Cat>, *, A>.target(): Obj<Opp<Cat>, A> = Opposite(value.source())
}

fun interface Arrow<A, B> : (A) -> B, K2<ArrowK, A, B>
typealias ArrowK = Arrow<*, *>

object ArrowCategory : Category<ArrowK> {
  override fun <A, B, C> K2<ArrowK, B, C>.compose(g: K2<ArrowK, A, B>): K2<ArrowK, A, C> = Arrow { a: A -> this(g(a)) }

  override fun <A> K2<ArrowK, A, *>.source(): Obj<ArrowK, A> = Arrow { a: A -> a }
  override fun <A> K2<ArrowK, *, A>.target(): Obj<ArrowK, A> = Arrow { a: A -> a }
}

data class PairK<A, B>(val first: A, val second: B) : K2<PairK<*, *>, A, B>

sealed interface MorphismProduct<C1, C2, P1, P2> : K2<Product<C1, C2>, P1, P2>
typealias Product<C1, C2> = K2<MorphismProduct<*, *, *, *>, C1, C2>

data class MorphismProductImpl<C1, C2, A1, B1, A2, B2>(
  val first: K2<C1, A1, B1>,
  val second: K2<C2, A2, B2>,
) : MorphismProduct<C1, C2, PairK<A1, A2>, PairK<B1, B2>>

@OptIn(ExperimentalContracts::class)
fun <C1, C2, P1, P2> MorphismProduct<C1, C2, P1, P2>.gadtMagic() {
  contract {
    returns() implies (this@gadtMagic is MorphismProductImpl<C1, C2, Any?, Any?, Any?, Any?>)
  }
}

context(_: Category<C1>, _: Category<C2>)
fun <C1, C2> productCategory(): Category<Product<C1, C2>> = object : Category<Product<C1, C2>> {
  override fun <PA, PB, PC> K2<Product<C1, C2>, PB, PC>.compose(g: K2<Product<C1, C2>, PA, PB>): K2<Product<C1, C2>, PA, PC> {
    gadtMagic()
    g.gadtMagic()
    return composeImpl(g) as MorphismProduct<C1, C2, PA, PC>
  }

  private fun <PA1, PA2, PB1, PB2, PC1, PC2> MorphismProductImpl<C1, C2, PB1, PC1, PB2, PC2>.composeImpl(
    g: MorphismProductImpl<C1, C2, PA1, PB1, PA2, PB2>,
  ): MorphismProductImpl<C1, C2, PA1, PC1, PA2, PC2> {
    val newFirst = first.compose<C1, PA1, PB1, PC1>(g.first)
    val newSecond = second.compose<C2, PA2, PB2, PC2>(g.second)
    return MorphismProductImpl(newFirst, newSecond)
  }

  override fun <P> K2<Product<C1, C2>, P, *>.source(): Obj<Product<C1, C2>, P> {
    gadtMagic()
    return MorphismProductImpl(first.source(), second.source()) as Obj<Product<C1, C2>, P>
  }

  override fun <P> K2<Product<C1, C2>, *, P>.target(): Obj<Product<C1, C2>, P> {
    gadtMagic()
    return MorphismProductImpl(first.target(), second.target()) as Obj<Product<C1, C2>, P>
  }
}

interface Functor<C, D, F> {
  val firstCategory: Category<C>
  val secondCategory: Category<D>
  fun <A, B> lift(f: K2<C, A, B>): K2<D, K<F, A>, K<F, B>>
}

context(functor: Functor<C, D, F>)
fun <C, D, F, A, B> lift(f: K2<C, A, B>): K2<D, K<F, A>, K<F, B>> = with(functor) { lift(f) }

typealias BiFunctor<C, D, E, F> = Functor<Product<C, D>, E, F>
typealias EndoBiFunctor<C, F> = BiFunctor<C, C, C, F>

typealias Component<D, F, G, A> = K2<D, K<F, A>, K<G, A>>

interface Nat<C, D, F, G> : K2<NatK<C, D>, F, G> {
  val firstFunctor: Functor<C, D, F>
  val secondFunctor: Functor<C, D, G>
  operator fun <A> get(c: Obj<C, A>): Component<D, F, G, A>
}
typealias NatK<C, D> = K2<Nat<*, *, *, *>, C, D>

fun <C, D, F, G, A, B> Nat<C, D, F, G>.at(h: K2<C, A, B>): K2<D, K<F, A>, K<G, B>> =
  context(firstFunctor, firstFunctor.firstCategory, firstFunctor.secondCategory) {
    get(h.target()) compose lift(h)
  }

typealias Endo<C, F, G> = Nat<C, C, F, G>
typealias EndoK<C> = NatK<C, C>
typealias NormalNat<F, G> = Endo<ArrowK, F, G>
typealias NormalNatK = EndoK<ArrowK>

interface Iso<C, D, F, G> : Nat<C, D, F, G> {
  val inv: Iso<C, D, G, F>
}

fun <C, D, F, G> Iso(
  to: Nat<C, D, F, G>,
  from: Nat<C, D, G, F>,
): Iso<C, D, F, G> = object : Iso<C, D, F, G>, Nat<C, D, F, G> by to {
  override val inv: Iso<C, D, G, F> = Iso(from, this)
}

private fun <C, D, F, G> Iso(
  to: Nat<C, D, F, G>,
  from: Iso<C, D, G, F>,
): Iso<C, D, F, G> = object : Iso<C, D, F, G>, Nat<C, D, F, G> by to {
  override val inv: Iso<C, D, G, F> = from
}

typealias IsoK<C, D> = K2<Iso<*, *, *, *>, C, D>

context(functor: Functor<C, D, F>)
fun <C, D, F> identityNat(): Iso<C, D, F, F> {
  val nat = object : Nat<C, D, F, F> {
    override val firstFunctor: Functor<C, D, F> = functor
    override val secondFunctor: Functor<C, D, F> = functor
    override fun <A> get(c: Obj<C, A>): Component<D, F, F, A> = lift(c)
  }
  return Iso(nat, nat)
}

fun box(): String {
  return "OK"
}