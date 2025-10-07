// RUN_PIPELINE_TILL: FRONTEND
// LANGUAGE_VERSION: 2.3
// ALLOW_DANGEROUS_LANGUAGE_VERSION_TESTING

import io.github.kyay10.highkt.*

interface Functor<F> {
  fun <A, B> Out<F, A>.fmap(f: (A) -> B): Out<F, B>
}

class Invariant<A>

fun Functor<Invariant<*>>.test(x: Invariant<Int>) {
  val y: Invariant<String> = <!INITIALIZER_TYPE_MISMATCH!>x.fmap { it.toString() }<!>
  val z: Invariant<out String> = x.fmap { it.toString() }
}

/* GENERATED_FIR_TAGS: classDeclaration, funWithExtensionReceiver, functionDeclaration, functionalType,
interfaceDeclaration, nullableType, starProjection, typeParameter */
