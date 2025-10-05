// RUN_PIPELINE_TILL: FRONTEND
// LANGUAGE_VERSION: 2.3
// ALLOW_DANGEROUS_LANGUAGE_VERSION_TESTING

import io.github.kyay10.highkt.*

@TypeFunction
interface Swapped<F, A, B>: K2<F, B, A>
typealias Swap<F> = K<Swapped<*, *, *>, F>

data class TripleK<A, B, C>(val first: A, val second: B, val third: C): K3<TripleK<*, *, *>, A, B, C>
fun test() {
  val foo = TripleK(1, "Hello", Unit)
  foo.expandTo<K3<TripleK<*, *, *>, Int, String, Unit>>() // OK
  foo.expandTo<K3<Swap<TripleK<*, *, *>>, String, Int, Unit>>() // OK
  <!EXPAND_TO_MISMATCH!>foo.expandTo<K3<Swap<TripleK<*, *, *>>, Int, String, Unit>>()<!> // Error
}

/* GENERATED_FIR_TAGS: classDeclaration, data, functionDeclaration, integerLiteral, interfaceDeclaration,
intersectionType, localProperty, nullableType, primaryConstructor, propertyDeclaration, smartcast, starProjection,
stringLiteral, typeAliasDeclaration, typeAliasDeclarationWithTypeParameter, typeParameter */
