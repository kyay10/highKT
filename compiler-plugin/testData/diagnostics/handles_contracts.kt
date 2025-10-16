// RUN_PIPELINE_TILL: BACKEND

import io.github.kyay10.highkt.*
import kotlin.contracts.*

@OptIn(ExperimentalContracts::class)
fun List<Any?>?.hasContract() {
  contract {
    returns() implies (this@hasContract != null)
  }
}

/* GENERATED_FIR_TAGS: enumDeclaration, enumEntry */
