package foo.bar

// LANGUAGE: +ContextParameters

import foo.bar.Product
import io.github.kyay10.highkt.*
import kotlin.contracts.*

typealias Obj<Cat, A> = K2<Cat, A, A>

interface Category<Cat> {
  infix fun <A, B, C> K2<Cat, B, C>.compose(g: K2<Cat, A, B>): K2<Cat, A, C>
  fun <A> K2<Cat, A, *>.source(): Obj<Cat, A>
  fun <A> K2<Cat, *, A>.target(): Obj<Cat, A>
}

context(category: Category<Cat>)
infix fun <Cat, A, B, C> K2<Cat, B, C>.compose(g: K2<Cat, A, B>) = with(category) { this@compose.compose(g) }

context(category: Category<Cat>)
fun <Cat, A> K2<Cat, A, *>.source() = with(category) { this@source.source<A>() }

context(category: Category<Cat>)
fun <Cat, A> K2<Cat, *, A>.target() = with(category) { this@target.target<A>() }

@TypeFunction
interface Opposite<Cat, A, B> : K2<Cat, B, A>
typealias Opp<Arr> = K<Opposite<*, *, *>, Arr>

context(c: Category<Cat>)
fun <Cat> oppositeCategory(): Category<Opp<Cat>> = object : Category<Opp<Cat>> {
  override fun <A, B, C> K2<Cat, C, B>.compose(g: K2<Cat, B, A>) = g compose this
  override fun <A> K2<Cat, *, A>.source() = target<Cat, _>()
  override fun <A> K2<Cat, A, *>.target() = source<Cat, _>()
}

typealias Arrow<A, B> = (A) -> B
typealias ArrowK = Arrow<*, *>

fun <A> idArrow() = { a: A -> a }

object ArrowCategory : Category<ArrowK> {
  override fun <A, B, C> ((B) -> C).compose(g: (A) -> B) = { a: A -> this(g(a)) }

  override fun <A> Arrow<A, *>.source() = idArrow<A>()
  override fun <A> Arrow<*, A>.target() = idArrow<A>()
}

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
  K2<Pair<*, *>, K2<C1, TypePairFirst<PA>, TypePairFirst<PB>>, K2<C2, TypePairSecond<PA>, TypePairSecond<PB>>>
typealias MorphismProducted<C1, C2, PA, PB> = Pair<K2<C1, TypePairFirst<PA>, TypePairFirst<PB>>, K2<C2, TypePairSecond<PA>, TypePairSecond<PB>>>
typealias Product<C1, C2> = K2<MorphismProduct<*, *, *, *>, C1, C2>

context(_: Category<C1>, _: Category<C2>)
fun <C1, C2> productCategory(): Category<Product<C1, C2>> = object : Category<Product<C1, C2>> {
  override fun <PA, PB, PC> MorphismProducted<C1, C2, PB, PC>.compose(g: MorphismProducted<C1, C2, PA, PB>) =
    first.compose(g.first) to second.compose(g.second)

  override fun <P> MorphismProducted<C1, C2, P, *>.source() = first.source() to second.source()

  override fun <P> MorphismProducted<C1, C2, *, P>.target() = first.target() to second.target()
}

interface Functor<C, D, F> {
  val firstCategory: Category<C>
  val secondCategory: Category<D>
  fun <A, B> lift(f: K2<C, A, B>): K2<D, K<F, A>, K<F, B>>
}

context(c: Category<C>)
fun <C> identityFunctor(): Functor<C, C, Identity> = object : Functor<C, C, Identity> {
  override val firstCategory = c
  override val secondCategory = c
  override fun <A, B> lift(f: K2<C, A, B>) = f
}

@TypeFunction
interface Composition<F, G, A> : K<F, K<G, A>>
typealias Compose<F, G> = K2<Composition<*, *, *>, F, G>

context(f: Functor<D, E, F>, g: Functor<C, D, G>)
fun <C, D, E, F, G> composeFunctors(): Functor<C, E, Compose<F, G>> = object : Functor<C, E, Compose<F, G>> {
  override val firstCategory = g.firstCategory
  override val secondCategory = f.secondCategory
  override fun <A, B> lift(h: K2<C, A, B>) = lift(g.lift(h))
}

context(functor: Functor<C, D, F>)
fun <C, D, F, A, B> lift(f: K2<C, A, B>) = with(functor) { lift(f) }

typealias BiFunctor<C, D, E, F> = Functor<Product<C, D>, E, F>
typealias EndoBiFunctor<C, F> = BiFunctor<C, C, C, F>

typealias Component<D, F, G, A> = K2<D, K<F, A>, K<G, A>>

interface Nat<C, D, F, G> : K2<NatK<C, D>, F, G> {
  val firstFunctor: Functor<C, D, F>
  val secondFunctor: Functor<C, D, G>
  operator fun <A> get(c: Obj<C, A>): Component<D, F, G, A>
}
typealias NatK<C, D> = K2<Nat<*, *, *, *>, C, D>

fun <C, D, F, G, A, B> Nat<C, D, F, G>.at(h: K2<C, A, B>) =
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
  override val inv = Iso(from, this)
}

private fun <C, D, F, G> Iso(
  to: Nat<C, D, F, G>,
  from: Iso<C, D, G, F>,
): Iso<C, D, F, G> = object : Iso<C, D, F, G>, Nat<C, D, F, G> by to {
  override val inv = from
}

typealias IsoK<C, D> = K2<Iso<*, *, *, *>, C, D>

context(functor: Functor<C, D, F>)
fun <C, D, F> identityNat(): Iso<C, D, F, F> {
  val nat = object : Nat<C, D, F, F> {
    override val firstFunctor = functor
    override val secondFunctor = functor
    override fun <A> get(c: Obj<C, A>) = lift(c)
  }
  return Iso(nat, nat)
}

infix fun <C, D, E, F, G, I, J> Nat<D, E, I, J>.horizontal(other: Nat<C, D, F, G>): Nat<C, E, Compose<I, F>, Compose<J, G>> =
  object : Nat<C, E, Compose<I, F>, Compose<J, G>> {
    override val firstFunctor =
      context(this@horizontal.firstFunctor, other.firstFunctor) { composeFunctors<C, D, E, I, F>() }
    override val secondFunctor =
      context(this@horizontal.secondFunctor, other.secondFunctor) { composeFunctors<C, D, E, J, G>() }

    override fun <A> get(c: Obj<C, A>) = this@horizontal.at(other.at(c))
  }

context(_: Category<D>)
infix fun <C, D, F, G, H> Nat<C, D, G, H>.vertical(other: Nat<C, D, F, G>): Nat<C, D, F, H> =
  object : Nat<C, D, F, H> {
    override val firstFunctor = other.firstFunctor
    override val secondFunctor = this@vertical.secondFunctor
    override fun <A> get(c: Obj<C, A>) = this@vertical[c] compose other[c]
  }

context(cd: Category<D>)
fun <C, D> functorCategory(): Category<NatK<C, D>> = object : Category<NatK<C, D>> {
  override fun <F, G, H> Nat<C, D, G, H>.compose(g: Nat<C, D, F, G>) = context(cd) { // KT-81441
    this vertical g
  }

  override fun <F> Nat<C, D, F, *>.source() = context(firstFunctor) { identityNat() }
  override fun <F> Nat<C, D, *, F>.target() = context(secondFunctor) { identityNat() }
}

@TypeFunction
interface FunctorCompose<P> : Compose<TypePairFirst<P>, TypePairSecond<P>>

context(_: Category<C>, _: Category<D>, _: Category<E>)
fun <C, D, E> functorComposeFunctor(): Functor<Product<NatK<D, E>, NatK<C, D>>, NatK<C, E>, FunctorCompose<*>> =
  object : Functor<Product<NatK<D, E>, NatK<C, D>>, NatK<C, E>, FunctorCompose<*>> {
    override val firstCategory = context(functorCategory<D, E>(), functorCategory<C, D>()) {
      productCategory<NatK<D, E>, NatK<C, D>>()
    }
    override val secondCategory = functorCategory<C, E>()

    override fun <A, B> lift(f: MorphismProducted<NatK<D, E>, NatK<C, D>, A, B>) = f.first horizontal f.second
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
    override val unitObject = context(identityFunctor<Cat>()) { identityNat<Cat, Cat, Identity>() }

    override fun <A> leftUnitor(a: Endo<Cat, A, A>) = context(a.firstFunctor, identityFunctor<Cat>()) {
      object : Nat<Cat, Cat, Compose<Identity, A>, A> {
        override val firstFunctor = composeFunctors<_, _, _, Identity, A>()
        override val secondFunctor = a.firstFunctor
        override fun <X> get(c: Obj<Cat, X>) = lift<_, _, A, _, _>(c)
      }
    }

    override fun <A> leftUnitorInv(a: Endo<Cat, A, A>) = context(a.firstFunctor, identityFunctor<Cat>()) {
      object : Nat<Cat, Cat, A, Compose<Identity, A>> {
        override val firstFunctor = a.firstFunctor
        override val secondFunctor = composeFunctors<_, _, _, Identity, A>()
        override fun <X> get(c: Obj<Cat, X>) = lift<_, _, A, _, _>(c)
      }
    }

    override fun <A> rightUnitor(a: Endo<Cat, A, A>) = context(a.firstFunctor, identityFunctor<Cat>()) {
      object : Nat<Cat, Cat, Compose<A, Identity>, A> {
        override val firstFunctor = composeFunctors<_, _, _, A, Identity>()
        override val secondFunctor = a.firstFunctor
        override fun <X> get(c: Obj<Cat, X>) = lift<_, _, A, _, _>(c)
      }
    }

    override fun <A> rightUnitorInv(a: Endo<Cat, A, A>) = context(a.firstFunctor, identityFunctor<Cat>()) {
      object : Nat<Cat, Cat, A, Compose<A, Identity>> {
        override val firstFunctor = a.firstFunctor
        override val secondFunctor = composeFunctors<_, _, _, A, Identity>()
        override fun <X> get(c: Obj<Cat, X>) = lift<_, _, A, _, _>(c)
      }
    }

    override fun <A, B, C> associator(
      a: Endo<Cat, A, A>,
      b: Endo<Cat, B, B>,
      c: Endo<Cat, C, C>
    ) = context(a.firstFunctor, b.firstFunctor, c.firstFunctor) {
      object : Nat<Cat, Cat, Compose<Compose<A, B>, C>, Compose<A, Compose<B, C>>> {
        override val firstFunctor: Functor<Cat, Cat, Compose<Compose<A, B>, C>> =
          context(composeFunctors<_, _, _, A, B>()) {
            composeFunctors<_, _, _, Compose<A, B>, C>()
          }
        override val secondFunctor: Functor<Cat, Cat, Compose<A, Compose<B, C>>> =
          context(composeFunctors<_, _, _, B, C>()) {
            composeFunctors<_, _, _, A, Compose<B, C>>()
          }

        override fun <X> get(c: Obj<Cat, X>) = lift<_, _, A, _, _>(lift<_, _, B, _, _>(lift<_, _, C, _, _>(c)))
      }
    }

    override fun <A, B, C> associatorInv(
      a: Endo<Cat, A, A>,
      b: Endo<Cat, B, B>,
      c: Endo<Cat, C, C>
    ) = context(a.firstFunctor, b.firstFunctor, c.firstFunctor) {
      object : Nat<Cat, Cat, Compose<A, Compose<B, C>>, Compose<Compose<A, B>, C>> {
        override val firstFunctor = context(composeFunctors<_, _, _, B, C>()) {
          composeFunctors<_, _, _, A, Compose<B, C>>()
        }
        override val secondFunctor = context(composeFunctors<_, _, _, A, B>()) {
          composeFunctors<_, _, _, Compose<A, B>, C>()
        }

        override fun <X> get(c: Obj<Cat, X>) = lift<_, _, A, _, _>(lift<_, _, B, _, _>(lift<_, _, C, _, _>(c)))
      }
    }
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
  override fun <A> pure(a: A) = empty()[idArrow<A>()](a)
  override fun <A, B> K<M, A>.bind(f: (A) -> K<M, B>) = context(plus().secondFunctor) {
    val mapped = lift(f)(this)
    plus()[idArrow<B>()](mapped)
  }
}

fun <M> UsualMonad<M>.toNormalFunctor(): Functor<ArrowK, ArrowK, M> = object : Functor<ArrowK, ArrowK, M> {
  override val firstCategory = ArrowCategory
  override val secondCategory = ArrowCategory
  override fun <A, B> lift(f: (A) -> B) = { ma: K<M, A> -> ma.bind { pure(f(it)) } }
}

fun <M> UsualMonad<M>.toNormalMonad(): NormalMonad<M> = context(toNormalFunctor(), ArrowCategory) {
  object : NormalMonad<M>,
    TensorProduct<EndoK<ArrowK>, FunctorCompose<*>, Identity> by endoFunctorComposeTensor<ArrowK>() {
    override fun empty() = object : Nat<ArrowK, ArrowK, Identity, M> {
      override val firstFunctor = identityFunctor<ArrowK>()
      override val secondFunctor = contextOf<Functor<ArrowK, ArrowK, M>>()
      override fun <A> get(c: (A) -> A) = { a: A -> pure(a) }
    }

    override fun plus() = object : Nat<ArrowK, ArrowK, Compose<M, M>, M> {
      override val firstFunctor = composeFunctors<ArrowK, ArrowK, ArrowK, M, M>()
      override val secondFunctor = contextOf<Functor<ArrowK, ArrowK, M>>()
      override fun <A> get(c: (A) -> A) = { mma: K<M, K<M, A>> -> mma.bind { ma -> ma } }
    }
  }
}

fun box(): String {
  return "OK"
}