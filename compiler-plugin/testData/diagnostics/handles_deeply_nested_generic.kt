// RUN_PIPELINE_TILL: BACKEND
data class Probable<T>(val weight: Double, val value: T)
sealed class Value<T> {
  data class Leaf<T>(val value: T) : Value<T>()
}
sealed class Option<out T>
object None : Option<Nothing>()
data class Some<out T>(val value: T) : Option<T>()

fun test() {
  listOf(
    Probable(0.125, Value.Leaf(Some(Triple(true, 21, 21)))),
    Probable(0.5, Value.Leaf(None)),
  )
}

/* GENERATED_FIR_TAGS: classDeclaration, data, funWithExtensionReceiver, functionDeclaration, intersectionType,
isExpression, nestedClass, nullableType, out, primaryConstructor, propertyDeclaration, sealed, smartcast, thisExpression,
typeParameter, whenExpression, whenWithSubject */
