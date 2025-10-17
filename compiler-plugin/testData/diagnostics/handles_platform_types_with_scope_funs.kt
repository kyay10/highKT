// RUN_PIPELINE_TILL: BACKEND

private val baseContClass = Class.forName("kotlin.coroutines.jvm.internal.BaseContinuationImpl").<!INFERRED_TYPE_VARIABLE_INTO_POSSIBLE_EMPTY_INTERSECTION!>apply<!> {}
private val contClass = Class.forName("kotlin.coroutines.jvm.internal.ContinuationImpl")
private val completionField = baseContClass.getDeclaredField("completion").apply { isAccessible = true }
private val contextField = contClass.getDeclaredField("_context").apply { isAccessible = true }

/* GENERATED_FIR_TAGS: assignment, flexibleType, javaFunction, lambdaLiteral, propertyDeclaration, stringLiteral */
