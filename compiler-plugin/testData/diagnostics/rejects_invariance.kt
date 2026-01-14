// RUN_PIPELINE_TILL: FRONTEND

import io.github.kyay10.highkt.Constructor
import io.github.kyay10.highkt.Out

interface Functor<F> {
  fun <A, B> Out<F, A>.fmap(f: (A) -> B): Out<F, B>
}

class Invariant<A>

fun Functor<Constructor<Invariant<*>>>.test(x: Invariant<Int>) {
  val y: Invariant<String> <!INITIALIZER_TYPE_MISMATCH!>=<!> x.fmap { it.toString() }
  val z: Invariant<out String> = x.fmap { it.toString() }
}

/* GENERATED_FIR_TAGS: classDeclaration, funWithExtensionReceiver, functionDeclaration, functionalType,
interfaceDeclaration, nullableType, starProjection, typeParameter */
