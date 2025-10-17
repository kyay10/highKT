// RUN_PIPELINE_TILL: BACKEND
data class Weighted<T>(val value: T, val weight: Double)

fun foo(): List<Weighted<Boolean>> = listOf()

@Suppress("UNCHECKED_CAST")
infix fun <T, U : T> T.shouldBe(expected: U?): T {
  return this
}

fun test() {
  foo() shouldBe listOf(Weighted(false, 0.099), Weighted(true, 0.0099))
}

/* GENERATED_FIR_TAGS: classDeclaration, data, funWithExtensionReceiver, functionDeclaration, intersectionType,
isExpression, nestedClass, nullableType, out, primaryConstructor, propertyDeclaration, sealed, smartcast, thisExpression,
typeParameter, whenExpression, whenWithSubject */
