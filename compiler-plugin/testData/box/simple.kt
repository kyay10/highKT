package foo.bar

// LANGUAGE: +ContextParameters

import org.jetbrains.kotlin.compiler.plugin.template.*

interface Functor<F> {
    fun <A, B> @K<A> F.fmap(f: (A) -> B): @K<B> F
}

interface Monad<M> : Functor<M> {
    fun <A> pure(a: A): @K<A> M

    fun <A, B> @K<A> M.bind(f: (A) -> @K<B> M): @K<B> M

    override fun <A, B> @K<A> M.fmap(f: (A) -> B) = bind<A, B> { a -> pure(f(a)) }
}

context(functor: Functor<F>)
fun <F, A, B> @K<A> F.fmap(f: (A) -> B) = with(functor) { fmap(f) }

context(monad: Monad<M>)
fun <M, A> pure(a: A) = with(monad) { pure(a) }

context(monad: Monad<M>)
fun <M, A, B> @K<A> M.bind(f: (A) -> @K<B> M) = with(monad) { bind<A, B>(f) }

object ListMonad : Monad<List<*>> {
    override fun <A> pure(a: A) = listOf(a)

    override fun <A, B> @K<A> List<*>.bind(f: (A) -> @K<B> List<*>) = this.flatMap(f)
}

fun box(): String = context(ListMonad as Functor<List<*>>) {
    val result: String = listOf("Hello").fmap { it: String -> "$it world" }.first()
    return if (result == "Hello world") { "OK" } else { "Fail: $result" }
}
