// RUN_PIPELINE_TILL: BACKEND

sealed class Either<out A, out B> {
  data class Left<A>(val a: A) : Either<A, Nothing>()
  data class Right<B>(val b: B) : Either<Nothing, B>()
}

fun <A> A.right(): Either<Nothing, A> = Either.Right(this)

val foo = 42.right()

fun test() {
  when (42.right()) {
    is Either.Left -> {}
    is Either.Right -> {}
  }
  when (foo) {
    is Either.Left -> {}
    is Either.Right -> {}
  }
  val bar = 42.right()
  when (bar) {
       <!USELESS_IS_CHECK!>is Either.Left<!> -> {}
       <!USELESS_IS_CHECK!>is Either.Right<!> -> {}
  }
}

/* GENERATED_FIR_TAGS: classDeclaration, data, funWithExtensionReceiver, functionDeclaration, intersectionType,
isExpression, nestedClass, nullableType, out, primaryConstructor, propertyDeclaration, sealed, smartcast, thisExpression,
typeParameter, whenExpression, whenWithSubject */
