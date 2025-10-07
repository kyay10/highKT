package io.github.kyay10.highkt.fir

import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

val PACKAGE_FQN = FqName("io.github.kyay10.highkt")
val ASSERT_IS_TYPE = Name.identifier("assertIsType")
val EXPAND_TO = Name.identifier("expandTo")
val EXPAND_TO_ID = CallableId(PACKAGE_FQN, EXPAND_TO)
val IDENTITY_CLASS_ID = ClassId(PACKAGE_FQN, Name.identifier("Id"))
val K_CLASS_ID = ClassId(PACKAGE_FQN, Name.identifier("K"))
val TYPE_FUNCTION_CLASS_ID = ClassId(PACKAGE_FQN, Name.identifier("TypeFunction"))