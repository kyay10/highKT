// RUN_PIPELINE_TILL: FRONTEND
// LANGUAGE_VERSION: 2.3
// ALLOW_DANGEROUS_LANGUAGE_VERSION_TESTING

import io.github.kyay10.highkt.*

@TypeFunction
interface Swapped<F, A, B>: K2<F, B, A>
typealias Swap<F> = K<Swapped<*, *, *>, F>

data class PairK<A, B>(val first: A, val second: B): K2<PairK<*, *>, A, B>
fun test() {
    val foo = PairK(1, "Hello")
    foo.expandTo<K2<PairK<*, *>, Int, String>>() // OK
    foo.expandTo<K2<Swap<PairK<*, *>>, String, Int>>() // OK
    <!EXPAND_TO_MISMATCH!>foo.expandTo<K2<Swap<PairK<*, *>>, Int, String>>()<!> // Error
}

/* GENERATED_FIR_TAGS: classDeclaration, data, functionDeclaration, integerLiteral, interfaceDeclaration,
intersectionType, localProperty, nullableType, primaryConstructor, propertyDeclaration, smartcast, starProjection,
stringLiteral, typeAliasDeclaration, typeAliasDeclarationWithTypeParameter, typeParameter */
