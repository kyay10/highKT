// RUN_PIPELINE_TILL: BACKEND

interface Handler<E>
class SubCont<in T, out R> {
  operator fun invoke(arg: T): R = TODO()
}

public fun <A, E> Handler<E>.useWithFinal(body: (Pair<SubCont<A, E>, SubCont<A, E>>) -> E): A {
  TODO()
}

class Scheduler2 : Handler<Unit> {
  suspend fun yieldAndRepush() = useWithFinal { (a, b) ->
    b(Unit)
  }
}

/* GENERATED_FIR_TAGS: enumDeclaration, enumEntry */
