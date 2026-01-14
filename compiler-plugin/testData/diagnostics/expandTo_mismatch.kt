// RUN_PIPELINE_TILL: FRONTEND

import io.github.kyay10.highkt.Constructor
import io.github.kyay10.highkt.K2
import io.github.kyay10.highkt.K
import io.github.kyay10.highkt.expandTo

typealias Swapped<F, A, B> = K2<F, B, A>
typealias Swap<F> = K<Constructor<Swapped<*, *, *>>, F>

typealias PairK = Constructor<Pair<*, *>>

fun test() {
  val foo = 1 to "Hello"
  foo.expandTo<K2<PairK, Int, String>>() // OK
  foo.expandTo<K2<Swap<PairK>, String, Int>>() // OK
  foo.<!UNRESOLVED_REFERENCE_WRONG_RECEIVER!>expandTo<!><K2<Swap<PairK>, Int, String>>() // Error
}

/* GENERATED_FIR_TAGS: classDeclaration, data, functionDeclaration, integerLiteral, interfaceDeclaration,
intersectionType, localProperty, nullableType, primaryConstructor, propertyDeclaration, smartcast, starProjection,
stringLiteral, typeAliasDeclaration, typeAliasDeclarationWithTypeParameter, typeParameter */
