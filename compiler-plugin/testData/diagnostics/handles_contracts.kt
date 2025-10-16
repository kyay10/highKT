// RUN_PIPELINE_TILL: BACKEND

import kotlin.contracts.*

@OptIn(ExperimentalContracts::class)
fun List<Any?>?.hasContract() {
  contract {
    returns() implies (this@hasContract != null)
  }
}

/* GENERATED_FIR_TAGS: enumDeclaration, enumEntry */
