// RUN_PIPELINE_TILL: BACKEND

import io.github.kyay10.highkt.*

private val baseContClass = Class.forName("kotlin.coroutines.jvm.internal.BaseContinuationImpl")
private val contClass = Class.forName("kotlin.coroutines.jvm.internal.ContinuationImpl")
private val completionField = baseContClass.getDeclaredField("completion").apply { isAccessible = true }
private val contextField = contClass.getDeclaredField("_context").apply { isAccessible = true }

/* GENERATED_FIR_TAGS: assignment, flexibleType, javaFunction, lambdaLiteral, propertyDeclaration, stringLiteral */
