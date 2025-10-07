package io.github.kyay10.highkt.ir

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrTypeOperator
import org.jetbrains.kotlin.ir.expressions.IrTypeOperatorCall
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.types.impl.buildSimpleType
import org.jetbrains.kotlin.ir.util.erasedUpperBound
import org.jetbrains.kotlin.ir.util.kotlinFqName
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.IrTypeTransformerVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid
import org.jetbrains.kotlin.name.FqName

private val K_FQNAME = FqName("io.github.kyay10.highkt.K")

class RemoveKCastsGenerationExtension : IrGenerationExtension {
  override fun generate(
    moduleFragment: IrModuleFragment, pluginContext: IrPluginContext
  ) {
    moduleFragment.transformChildrenVoid(RemoveKCastsTransformer())
    moduleFragment.acceptChildrenVoid(EraseKTypesTransformer(pluginContext))
  }
}

// Not strictly necessary, but helps clean the code a bit
class RemoveKCastsTransformer : IrElementTransformerVoid() {
  override fun visitTypeOperator(expression: IrTypeOperatorCall): IrExpression {
    if (expression.operator == IrTypeOperator.CAST || expression.operator == IrTypeOperator.IMPLICIT_CAST || expression.operator == IrTypeOperator.REINTERPRET_CAST) {
      if (expression.type.classFqName == K_FQNAME) {
        return expression.argument.transform(this, null)
      }
    }
    return expression
  }
}

class EraseKTypesTransformer(val pluginContext: IrPluginContext) : IrTypeTransformerVoid() {
  @Suppress("UNCHECKED_CAST")
  override fun <Type : IrType?> transformTypeRecursively(
    container: IrElement, type: Type
  ): Type = when {
    type !is IrSimpleType -> type
    type.erasedUpperBound.kotlinFqName == K_FQNAME -> pluginContext.irBuiltIns.anyNType as Type
    else -> type.buildSimpleType {
      arguments = type.arguments.map {
        if (it !is IrType) return@map it
        transformTypeRecursively(container, it)
      }
    } as Type
  }
}