// RUN_PIPELINE_TILL: FRONTEND

import io.github.kyay10.highkt.*

@TypeFunction
interface Swapped<F, A, B> : K2<F, B, A>
typealias Swap<F> = K<Swapped<*, *, *>, F>

fun test() {
  val foo = 1 to "Hello"
  foo.expandTo<K2<Pair<*, *>, Int, String>>() // OK
  foo.expandTo<K2<Swap<Pair<*, *>>, String, Int>>() // OK
  <!EXPAND_TO_MISMATCH!>foo.expandTo<K2<Swap<Pair<*, *>>, Int, String>>()<!> // Error
}

/* GENERATED_FIR_TAGS: classDeclaration, data, functionDeclaration, integerLiteral, interfaceDeclaration,
intersectionType, localProperty, nullableType, primaryConstructor, propertyDeclaration, smartcast, starProjection,
stringLiteral, typeAliasDeclaration, typeAliasDeclarationWithTypeParameter, typeParameter */
