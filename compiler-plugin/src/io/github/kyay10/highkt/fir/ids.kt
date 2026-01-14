package io.github.kyay10.highkt.fir

import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

val PACKAGE_FQN = FqName("io.github.kyay10.highkt")
val IDENTITY_CLASS_ID = ClassId(PACKAGE_FQN, Name.identifier("Id"))
val K_CLASS_ID = ClassId(PACKAGE_FQN, Name.identifier("K"))
val TYPE_FUNCTION_CLASS_ID = ClassId(PACKAGE_FQN, Name.identifier("TypeFunction"))
val CONSTRUCTOR_CLASS_ID = ClassId(PACKAGE_FQN, Name.identifier("Constructor"))