// RUN_PIPELINE_TILL: BACKEND

fun test() {
  val (_, b) = listOf(42) to listOf("foo")
}

/* GENERATED_FIR_TAGS: classDeclaration, data, funWithExtensionReceiver, functionDeclaration, intersectionType,
isExpression, nestedClass, nullableType, out, primaryConstructor, propertyDeclaration, sealed, smartcast, thisExpression,
typeParameter, whenExpression, whenWithSubject */
