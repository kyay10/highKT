// RUN_PIPELINE_TILL: FRONTEND

import io.github.kyay10.highkt.TypeFunction
import io.github.kyay10.highkt.expandTo
import io.github.kyay10.highkt.K3
import io.github.kyay10.highkt.K2
import io.github.kyay10.highkt.K

@TypeFunction
interface Swapped<F, A, B> : K2<F, B, A>
typealias Swap<F> = K<Swapped<*, *, *>, F>

fun test() {
  val foo = Triple(1, "Hello", Unit)
  foo.expandTo<K3<Triple<*, *, *>, Int, String, Unit>>() // OK
  foo.expandTo<K3<Swap<Triple<*, *, *>>, String, Int, Unit>>() // OK
  foo.<!UNRESOLVED_REFERENCE_WRONG_RECEIVER!>expandTo<!><K3<Swap<Triple<*, *, *>>, Int, String, Unit>>() // Error
}

/* GENERATED_FIR_TAGS: classDeclaration, data, functionDeclaration, integerLiteral, interfaceDeclaration,
intersectionType, localProperty, nullableType, primaryConstructor, propertyDeclaration, smartcast, starProjection,
stringLiteral, typeAliasDeclaration, typeAliasDeclarationWithTypeParameter, typeParameter */
