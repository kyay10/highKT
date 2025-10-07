// RUN_PIPELINE_TILL: FRONTEND
// LANGUAGE_VERSION: 2.3
// ALLOW_DANGEROUS_LANGUAGE_VERSION_TESTING

import io.github.kyay10.highkt.*

@TypeFunction
interface Swapped<F, A, B> : K2<F, B, A>
typealias Swap<F> = K<Swapped<*, *, *>, F>

fun test() {
  <!INFERRED_TYPE_VARIABLE_INTO_POSSIBLE_EMPTY_INTERSECTION!>val foo = Triple(1, "Hello", Unit)<!>
  foo.expandTo<K3<Triple<*, *, *>, Int, String, Unit>>() // OK
  foo.expandTo<K3<Swap<Triple<*, *, *>>, String, Int, Unit>>() // OK
  <!EXPAND_TO_MISMATCH!>foo.expandTo<K3<Swap<Triple<*, *, *>>, Int, String, Unit>>()<!> // Error
}

/* GENERATED_FIR_TAGS: classDeclaration, data, functionDeclaration, integerLiteral, interfaceDeclaration,
intersectionType, localProperty, nullableType, primaryConstructor, propertyDeclaration, smartcast, starProjection,
stringLiteral, typeAliasDeclaration, typeAliasDeclarationWithTypeParameter, typeParameter */
