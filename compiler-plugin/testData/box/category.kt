package foo.bar

// LANGUAGE: +ContextParameters +AllowCheckForErasedTypesInContracts
// LANGUAGE_VERSION: 2.3
// ALLOW_DANGEROUS_LANGUAGE_VERSION_TESTING

import foo.bar.Product
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

@TypeFunction
interface Opposite<Cat, A, B> : K2<Cat, B, A>
typealias Opp<Arr> = K<Opposite<*, *, *>, Arr>

context(c: Category<Cat>)
fun <Cat> oppositeCategory(): Category<Opp<Cat>> = object : Category<Opp<Cat>> {
  override fun <A, B, C> K2<Opp<Cat>, B, C>.compose(g: K2<Opp<Cat>, A, B>): K2<Opp<Cat>, A, C> = context(c) {
    g.compose<Cat, _, _, _>(this)
  }.expandTo()

  override fun <A> K2<Opp<Cat>, A, *>.source(): Obj<Opp<Cat>, A> = context(c) {
    target<Cat, _>()
  }.expandTo()

  override fun <A> K2<Opp<Cat>, *, A>.target(): Obj<Opp<Cat>, A> = context(c) {
    source<Cat, _>()
  }.expandTo()
}

fun interface Arrow<A, B> : (A) -> B, K2<ArrowK, A, B>
typealias ArrowK = Arrow<*, *>

fun <A> idArrow(): Arrow<A, A> = Arrow { a: A -> a }

object ArrowCategory : Category<ArrowK> {
  override fun <A, B, C> K2<ArrowK, B, C>.compose(g: K2<ArrowK, A, B>): K2<ArrowK, A, C> = Arrow { a: A -> this(g(a)) }

  override fun <A> K2<ArrowK, A, *>.source(): Obj<ArrowK, A> = idArrow<A>()
  override fun <A> K2<ArrowK, *, A>.target(): Obj<ArrowK, A> = idArrow<A>()
}

data class PairK<A, B>(val first: A, val second: B) : K2<PairK<*, *>, A, B>

@TypeFunction
interface TypePaired<A, B, F> : K2<F, A, B>
typealias TypePair<A, B> = K2<TypePaired<*, *, *>, A, B>

@TypeFunction
interface TypePairedFirst<A, B> : Id<A>
typealias TypePairFirst<P> = K<P, TypePairedFirst<*, *>>

@TypeFunction
interface TypePairedSecond<A, B> : Id<B>
typealias TypePairSecond<P> = K<P, TypePairedSecond<*, *>>

@TypeFunction
interface MorphismProduct<C1, C2, PA, PB> :
  K2<PairK<*, *>, K2<C1, TypePairFirst<PA>, TypePairFirst<PB>>, K2<C2, TypePairSecond<PA>, TypePairSecond<PB>>>
typealias Product<C1, C2> = K2<MorphismProduct<*, *, *, *>, C1, C2>

context(_: Category<C1>, _: Category<C2>)
fun <C1, C2> productCategory(): Category<Product<C1, C2>> = object : Category<Product<C1, C2>> {
  override fun <PA, PB, PC> K2<Product<C1, C2>, PB, PC>.compose(g: K2<Product<C1, C2>, PA, PB>): K2<Product<C1, C2>, PA, PC> {
    val newFirst = first.compose(g.first)
    val newSecond = second.compose(g.second)
    return PairK(newFirst, newSecond).expandTo()
  }

  override fun <P> K2<Product<C1, C2>, P, *>.source(): Obj<Product<C1, C2>, P> =
    PairK(first.source(), second.source()).expandTo()

  override fun <P> K2<Product<C1, C2>, *, P>.target(): Obj<Product<C1, C2>, P> =
    PairK(first.target(), second.target()).expandTo()
}

interface Functor<C, D, F> {
  val firstCategory: Category<C>
  val secondCategory: Category<D>
  fun <A, B> lift(f: K2<C, A, B>): K2<D, K<F, A>, K<F, B>>
}

context(c: Category<C>)
fun <C> identityFunctor(): Functor<C, C, Identity> = object : Functor<C, C, Identity> {
  override val firstCategory: Category<C> = c
  override val secondCategory: Category<C> = c
  override fun <A, B> lift(f: K2<C, A, B>): K2<C, K<Identity, A>, K<Identity, B>> = f.expandTo()
}

@TypeFunction
interface Composition<F, G, A> : K<F, K<G, A>>
typealias Compose<F, G> = K2<Composition<*, *, *>, F, G>

context(f: Functor<D, E, F>, g: Functor<C, D, G>)
fun <C, D, E, F, G> composeFunctors(): Functor<C, E, Compose<F, G>> = object : Functor<C, E, Compose<F, G>> {
  override val firstCategory: Category<C> = g.firstCategory
  override val secondCategory: Category<E> = f.secondCategory
  override fun <A, B> lift(h: K2<C, A, B>): K2<E, K<Compose<F, G>, A>, K<Compose<F, G>, B>> =
    lift(lift<_, _, G, _, _>(h)).expandTo()
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

infix fun <C, D, E, F, G, I, J> Nat<D, E, I, J>.horizontal(other: Nat<C, D, F, G>): Nat<C, E, Compose<I, F>, Compose<J, G>> =
  object : Nat<C, E, Compose<I, F>, Compose<J, G>> {
    override val firstFunctor: Functor<C, E, Compose<I, F>> =
      context(this@horizontal.firstFunctor, other.firstFunctor) { composeFunctors<C, D, E, I, F>() }
    override val secondFunctor: Functor<C, E, Compose<J, G>> =
      context(this@horizontal.secondFunctor, other.secondFunctor) { composeFunctors<C, D, E, J, G>() }

    override fun <A> get(c: Obj<C, A>): Component<E, Compose<I, F>, Compose<J, G>, A> =
      this@horizontal.at(other.at(c)).expandTo()
  }

context(_: Category<D>)
infix fun <C, D, F, G, H> Nat<C, D, G, H>.vertical(other: Nat<C, D, F, G>): Nat<C, D, F, H> =
  object : Nat<C, D, F, H> {
    override val firstFunctor: Functor<C, D, F> = other.firstFunctor
    override val secondFunctor: Functor<C, D, H> = this@vertical.secondFunctor
    override fun <A> get(c: Obj<C, A>): Component<D, F, H, A> = this@vertical[c] compose other[c]
  }

context(cd: Category<D>)
fun <C, D> functorCategory(): Category<NatK<C, D>> = object : Category<NatK<C, D>> {
  override fun <F, G, H> K2<NatK<C, D>, G, H>.compose(g: K2<NatK<C, D>, F, G>): K2<NatK<C, D>, F, H> =
    context(cd) { // KT-81441
      this vertical g
    }

  override fun <F> K2<NatK<C, D>, F, *>.source(): Obj<NatK<C, D>, F> = context(firstFunctor) { identityNat() }
  override fun <F> K2<NatK<C, D>, *, F>.target(): Obj<NatK<C, D>, F> = context(secondFunctor) { identityNat() }
}

@TypeFunction
interface FunctorCompose<P> : Compose<TypePairFirst<P>, TypePairSecond<P>>

context(_: Category<C>, _: Category<D>, _: Category<E>)
fun <C, D, E> functorComposeFunctor(): Functor<Product<NatK<D, E>, NatK<C, D>>, NatK<C, E>, FunctorCompose<*>> =
  object : Functor<Product<NatK<D, E>, NatK<C, D>>, NatK<C, E>, FunctorCompose<*>> {
    override val firstCategory: Category<Product<NatK<D, E>, NatK<C, D>>> =
      context(functorCategory<D, E>(), functorCategory<C, D>()) {
        productCategory<NatK<D, E>, NatK<C, D>>()
      }
    override val secondCategory: Category<NatK<C, E>> = functorCategory<C, E>()

    override fun <A, B> lift(f: K2<Product<NatK<D, E>, NatK<C, D>>, A, B>): K2<NatK<C, E>, K<FunctorCompose<*>, A>, K<FunctorCompose<*>, B>> =
      (f.first horizontal f.second).expandTo()
  }

interface TensorProduct<Cat, F, I> : Functor<Product<Cat, Cat>, Cat, F> {
  val unitObject: Obj<Cat, I>
  fun <A> leftUnitor(a: Obj<Cat, A>): K2<Cat, K<F, TypePair<I, A>>, A>
  fun <A> leftUnitorInv(a: Obj<Cat, A>): K2<Cat, A, K<F, TypePair<I, A>>>
  fun <A> rightUnitor(a: Obj<Cat, A>): K2<Cat, K<F, TypePair<A, I>>, A>
  fun <A> rightUnitorInv(a: Obj<Cat, A>): K2<Cat, A, K<F, TypePair<A, I>>>
  fun <A, B, C> associator(
    a: Obj<Cat, A>,
    b: Obj<Cat, B>,
    c: Obj<Cat, C>
  ): K2<Cat, K<F, TypePair<K<F, TypePair<A, B>>, C>>, K<F, TypePair<A, K<F, TypePair<B, C>>>>>

  fun <A, B, C> associatorInv(
    a: Obj<Cat, A>,
    b: Obj<Cat, B>,
    c: Obj<Cat, C>
  ): K2<Cat, K<F, TypePair<A, K<F, TypePair<B, C>>>>, K<F, TypePair<K<F, TypePair<A, B>>, C>>>
}

context(k: Category<Cat>)
fun <Cat> endoFunctorComposeTensor(): TensorProduct<EndoK<Cat>, FunctorCompose<*>, Identity> =
  object : TensorProduct<EndoK<Cat>, FunctorCompose<*>, Identity>,
    Functor<Product<EndoK<Cat>, EndoK<Cat>>, EndoK<Cat>, FunctorCompose<*>> by functorComposeFunctor<Cat, Cat, Cat>() {
    override val unitObject: Obj<EndoK<Cat>, Identity> =
      context(identityFunctor<Cat>()) { identityNat<Cat, Cat, Identity>() }

    override fun <A> leftUnitor(a: Obj<EndoK<Cat>, A>): K2<EndoK<Cat>, K<FunctorCompose<*>, TypePair<Identity, A>>, A> =
      context(a.firstFunctor, identityFunctor<Cat>()) {
        object : Nat<Cat, Cat, Compose<Identity, A>, A> {
          override val firstFunctor: Functor<Cat, Cat, Compose<Identity, A>> = composeFunctors<_, _, _, Identity, A>()
          override val secondFunctor: Functor<Cat, Cat, A> = a.firstFunctor
          override fun <X> get(c: Obj<Cat, X>): Component<Cat, Compose<Identity, A>, A, X> =
            lift<_, _, A, _, _>(c).expandTo()
        }
      }.expandTo()

    override fun <A> leftUnitorInv(a: Obj<EndoK<Cat>, A>): K2<EndoK<Cat>, A, K<FunctorCompose<*>, TypePair<Identity, A>>> =
      context(a.firstFunctor, identityFunctor<Cat>()) {
        object : Nat<Cat, Cat, A, Compose<Identity, A>> {
          override val firstFunctor: Functor<Cat, Cat, A> = a.firstFunctor
          override val secondFunctor: Functor<Cat, Cat, Compose<Identity, A>> = composeFunctors<_, _, _, Identity, A>()
          override fun <X> get(c: Obj<Cat, X>): Component<Cat, A, Compose<Identity, A>, X> =
            lift<_, _, A, _, _>(c).expandTo()
        }
      }.expandTo()

    override fun <A> rightUnitor(a: Obj<EndoK<Cat>, A>): K2<EndoK<Cat>, K<FunctorCompose<*>, TypePair<A, Identity>>, A> =
      context(a.firstFunctor, identityFunctor<Cat>()) {
        object : Nat<Cat, Cat, Compose<A, Identity>, A> {
          override val firstFunctor: Functor<Cat, Cat, Compose<A, Identity>> = composeFunctors<_, _, _, A, Identity>()
          override val secondFunctor: Functor<Cat, Cat, A> = a.firstFunctor
          override fun <X> get(c: Obj<Cat, X>): Component<Cat, Compose<A, Identity>, A, X> =
            lift<_, _, A, _, _>(c).expandTo()
        }
      }.expandTo()

    override fun <A> rightUnitorInv(a: Obj<EndoK<Cat>, A>): K2<EndoK<Cat>, A, K<FunctorCompose<*>, TypePair<A, Identity>>> =
      context(a.firstFunctor, identityFunctor<Cat>()) {
        object : Nat<Cat, Cat, A, Compose<A, Identity>> {
          override val firstFunctor: Functor<Cat, Cat, A> = a.firstFunctor
          override val secondFunctor: Functor<Cat, Cat, Compose<A, Identity>> = composeFunctors<_, _, _, A, Identity>()
          override fun <X> get(c: Obj<Cat, X>): Component<Cat, A, Compose<A, Identity>, X> =
            lift<_, _, A, _, _>(c).expandTo()
        }
      }.expandTo()

    override fun <A, B, C> associator(
      a: Obj<EndoK<Cat>, A>,
      b: Obj<EndoK<Cat>, B>,
      c: Obj<EndoK<Cat>, C>
    ): K2<EndoK<Cat>, K<FunctorCompose<*>, TypePair<K<FunctorCompose<*>, TypePair<A, B>>, C>>, K<FunctorCompose<*>, TypePair<A, K<FunctorCompose<*>, TypePair<B, C>>>>> =
      context(a.firstFunctor, b.firstFunctor, c.firstFunctor) {
        object : Nat<Cat, Cat, Compose<Compose<A, B>, C>, Compose<A, Compose<B, C>>> {
          override val firstFunctor: Functor<Cat, Cat, Compose<Compose<A, B>, C>> =
            context(composeFunctors<_, _, _, A, B>()) {
              composeFunctors<_, _, _, Compose<A, B>, C>()
            }
          override val secondFunctor: Functor<Cat, Cat, Compose<A, Compose<B, C>>> =
            context(composeFunctors<_, _, _, B, C>()) {
              composeFunctors<_, _, _, A, Compose<B, C>>()
            }

          override fun <X> get(c: Obj<Cat, X>): Component<Cat, Compose<Compose<A, B>, C>, Compose<A, Compose<B, C>>, X> =
            lift<_, _, A, _, _>(lift<_, _, B, _, _>(lift<_, _, C, _, _>(c))).expandTo()
        }
      }.expandTo()

    override fun <A, B, C> associatorInv(
      a: Obj<EndoK<Cat>, A>,
      b: Obj<EndoK<Cat>, B>,
      c: Obj<EndoK<Cat>, C>
    ): K2<EndoK<Cat>, K<FunctorCompose<*>, TypePair<A, K<FunctorCompose<*>, TypePair<B, C>>>>, K<FunctorCompose<*>, TypePair<K<FunctorCompose<*>, TypePair<A, B>>, C>>> =
      context(a.firstFunctor, b.firstFunctor, c.firstFunctor) {
        object : Nat<Cat, Cat, Compose<A, Compose<B, C>>, Compose<Compose<A, B>, C>> {
          override val firstFunctor: Functor<Cat, Cat, Compose<A, Compose<B, C>>> =
            context(composeFunctors<_, _, _, B, C>()) {
              composeFunctors<_, _, _, A, Compose<B, C>>()
            }
          override val secondFunctor: Functor<Cat, Cat, Compose<Compose<A, B>, C>> =
            context(composeFunctors<_, _, _, A, B>()) {
              composeFunctors<_, _, _, Compose<A, B>, C>()
            }

          override fun <X> get(c: Obj<Cat, X>): Component<Cat, Compose<A, Compose<B, C>>, Compose<Compose<A, B>, C>, X> =
            lift<_, _, A, _, _>(lift<_, _, B, _, _>(lift<_, _, C, _, _>(c))).expandTo()
        }
      }.expandTo()
  }

interface MonoidObject<Cat, F, I, A> : TensorProduct<Cat, F, I> {
  fun empty(): K2<Cat, I, A>
  fun plus(): K2<Cat, K<F, TypePair<A, A>>, A>
}

typealias Monad<C, F> = MonoidObject<EndoK<C>, FunctorCompose<*>, Identity, F>
typealias NormalMonad<F> = Monad<ArrowK, F>

interface UsualMonad<M> {
  fun <A> pure(a: A): K<M, A>
  fun <A, B> K<M, A>.bind(f: (A) -> K<M, B>): K<M, B>
}

fun <M> NormalMonad<M>.toUsualMonad(): UsualMonad<M> = object : UsualMonad<M> {
  override fun <A> pure(a: A): K<M, A> = empty().get(idArrow<A>())(a)
  override fun <A, B> K<M, A>.bind(f: (A) -> K<M, B>): K<M, B> =
    context(plus().secondFunctor) {
      val mapped = lift(Arrow(f))(this)
      plus().get(idArrow<B>())(mapped)
    }
}

fun <M> UsualMonad<M>.toNormalFunctor(): Functor<ArrowK, ArrowK, M> = object : Functor<ArrowK, ArrowK, M> {
  override val firstCategory: Category<ArrowK> = ArrowCategory
  override val secondCategory: Category<ArrowK> = ArrowCategory
  override fun <A, B> lift(f: K2<ArrowK, A, B>): K2<ArrowK, K<M, A>, K<M, B>> = Arrow { it.bind { pure(f(it)) } }
}

fun <M> UsualMonad<M>.toNormalMonad(): NormalMonad<M> = context(toNormalFunctor(), ArrowCategory) {
  object : NormalMonad<M>,
    TensorProduct<EndoK<ArrowK>, FunctorCompose<*>, Identity> by endoFunctorComposeTensor<ArrowK>() {
    override fun empty(): K2<EndoK<ArrowK>, Identity, M> = object : Nat<ArrowK, ArrowK, Identity, M> {
      override val firstFunctor: Functor<ArrowK, ArrowK, Identity> = identityFunctor<ArrowK>()
      override val secondFunctor: Functor<ArrowK, ArrowK, M> = contextOf<Functor<ArrowK, ArrowK, M>>()
      override fun <A> get(c: Obj<ArrowK, A>): Component<ArrowK, Identity, M, A> = Arrow { a: A -> pure(a) }.expandTo()
    }

    override fun plus(): K2<EndoK<ArrowK>, K<FunctorCompose<*>, TypePair<M, M>>, M> =
      object : Nat<ArrowK, ArrowK, Compose<M, M>, M> {
        override val firstFunctor: Functor<ArrowK, ArrowK, Compose<M, M>> =
          composeFunctors<ArrowK, ArrowK, ArrowK, M, M>()
        override val secondFunctor: Functor<ArrowK, ArrowK, M> = contextOf<Functor<ArrowK, ArrowK, M>>()
        override fun <A> get(c: Obj<ArrowK, A>): Component<ArrowK, Compose<M, M>, M, A> = Arrow { mma: K<M, K<M, A>> ->
          mma.bind { ma -> ma }
        }.expandTo()
      }.expandTo()
  }
}

fun box(): String {
  return "OK"
}