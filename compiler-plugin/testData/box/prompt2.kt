package foo.bar

// LANGUAGE: +ContextParameters

import io.github.kyay10.highkt.*

class Prompt<R>

interface Monad<F> {
  fun <A> pure(a: A): K<F, A>
  fun <A, B> K<F, A>.flatMap(f: suspend (A) -> K<F, B>): K<F, B>
}

context(m: Monad<F>)
fun <F, A> pure(a: A): K<F, A> = m.pure(a)

context(m: Monad<F>)
fun <F, A, B> K<F, A>.flatMap(f: suspend (A) -> K<F, B>): K<F, B> = with(m) { this@flatMap.flatMap(f) }

context(_: Prompt<K<F, A>>, _: Monad<F>)
suspend fun <F, A, B> K<F, B>.bind(): B = throw NotImplementedError()

context(_: Monad<F>)
suspend fun <F, A> monadReset(body: suspend Prompt<K<F, A>>.() -> A): K<F, A> = throw NotImplementedError()

suspend fun <F, A> Monad<F>.reset(body: suspend context(Monad<F>) Prompt<K<F, A>>.() -> A) = monadReset { body() }

typealias State<S, A> = suspend (S) -> Pair<A, S>
typealias StateOf<S> = K<Constructor<State<*, *>>, S>

class StateMonad<S> : Monad<StateOf<S>> {
  override fun <A> pure(a: A) = suspend { s: S -> Pair(a, s) }
  override fun <A, B> State<S, A>.flatMap(f: suspend (A) -> State<S, B>) = suspend { s0: S ->
    val (a, s1) = this(s0)
    f(a)(s1)
  }
}

suspend fun stateMonad() {
  data class CounterState(val count: Int)

  val incrementCounter: State<CounterState, Unit> = suspend { state: CounterState ->
    Unit to state.copy(count = state.count + 1)
  }

  val doubleCounter: State<CounterState, Unit> = suspend { state: CounterState ->
    Unit to state.copy(count = state.count * 2)
  }

  val result = StateMonad<CounterState>().reset {
    incrementCounter.bind()
    doubleCounter.bind()
    doubleCounter.bind()
  }(CounterState(0))

  val expected = context(StateMonad<CounterState>()) {
    incrementCounter.flatMap { doubleCounter.flatMap { doubleCounter } }(CounterState(0))
  }
}

fun box(): String {
  return "OK"
}