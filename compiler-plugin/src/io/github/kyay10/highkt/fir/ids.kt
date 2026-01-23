package io.github.kyay10.highkt.fir

import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

val PACKAGE_FQN = FqName("io.github.kyay10.highkt")
val IDENTITY_CLASS_ID = ClassId(PACKAGE_FQN, Name.identifier("Identity"))
val K_CLASS_ID = ClassId(PACKAGE_FQN, Name.identifier("K"))
val CONSTRUCTOR_CLASS_ID = ClassId(PACKAGE_FQN, Name.identifier("Constructor"))
