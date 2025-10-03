package io.github.kyay10.highkt.fir

import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.types.Variance

val PACKAGE_FQN = FqName("io.github.kyay10.highkt")
val ASSERT_IS_TYPE = Name.identifier("assertIsType")
val OUT_CLASS_ID = ClassId(PACKAGE_FQN, Name.identifier("Out"))
val IN_CLASS_ID = ClassId(PACKAGE_FQN, Name.identifier("In"))
val K_CLASS_ID = ClassId(PACKAGE_FQN, Name.identifier("K"))
val K_VARIANCES = mapOf(
  IN_CLASS_ID to Variance.IN_VARIANCE,
  OUT_CLASS_ID to Variance.OUT_VARIANCE,
  K_CLASS_ID to Variance.INVARIANT
)
val K_IDS = K_VARIANCES.keys