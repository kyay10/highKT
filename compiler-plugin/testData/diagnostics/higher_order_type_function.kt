// RUN_PIPELINE_TILL: FRONTEND

import io.github.kyay10.highkt.Constructor
import io.github.kyay10.highkt.expandTo
import io.github.kyay10.highkt.K3
import io.github.kyay10.highkt.K2
import io.github.kyay10.highkt.K

typealias Swapped<F, A, B> = K2<F, B, A>
typealias Swap<F> = K<Constructor<Swapped<*, *, *>>, F>

typealias TripleK = Constructor<Triple<*, *, *>>

fun test() {
  val foo = Triple(1, "Hello", Unit)
  foo.expandTo<K3<TripleK, Int, String, Unit>>() // OK
  foo.expandTo<K3<Swap<TripleK>, String, Int, Unit>>() // OK
  foo.<!UNRESOLVED_REFERENCE_WRONG_RECEIVER!>expandTo<!><K3<Swap<TripleK>, Int, String, Unit>>() // Error
}

/* GENERATED_FIR_TAGS: classDeclaration, data, functionDeclaration, integerLiteral, interfaceDeclaration,
intersectionType, localProperty, nullableType, primaryConstructor, propertyDeclaration, smartcast, starProjection,
stringLiteral, typeAliasDeclaration, typeAliasDeclarationWithTypeParameter, typeParameter */
