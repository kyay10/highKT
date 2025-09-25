package foo.bar

// LANGUAGE: +ContextParameters

import org.jetbrains.kotlin.compiler.plugin.template.K

interface Functor<F> {
    fun <A, B> @K<A> F.fmap(f: (A) -> B): @K<B> F
}

context(functor: Functor<F>)
fun <F, A, B> @K<A> F.fmap(f: (A) -> B): @K<B> F = with(functor) { fmap(f) }

object ListFunctor : Functor<List<*>> {
    override fun <A, B> @K<A> List<*>.fmap(f: (A) -> B): @K<B> List<*> = this.map(f)
}

fun box(): String = context(ListFunctor as Functor<List<*>>) {
    val result: String = listOf("Hello").fmap<_, String, _> { "$it world" }.first()
    return if (result == "Hello world") { "OK" } else { "Fail: $result" }
}
