// RUN_PIPELINE_TILL: BACKEND
// LANGUAGE: +ContextParameters

class Amb
class Exc {
  fun raise(): Nothing = TODO()
}

class Stream<A>

context(e: Exc)
fun raise(): Nothing = e.raise()

context(_: Amb, _: Exc)
suspend fun <A> Stream<A>?.reflect(): A =
  (<!INFERRED_TYPE_VARIABLE_INTO_POSSIBLE_EMPTY_INTERSECTION!>this ?: raise()<!>).reflect()

/* GENERATED_FIR_TAGS: classDeclaration, elvisExpression, funWithExtensionReceiver, functionDeclaration,
functionDeclarationWithContext, intersectionType, nullableType, smartcast, starProjection, suspend, thisExpression,
typeParameter */
