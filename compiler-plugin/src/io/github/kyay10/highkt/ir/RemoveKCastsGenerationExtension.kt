package io.github.kyay10.highkt.ir

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.jvm.JvmIrTypeSystemContext
import org.jetbrains.kotlin.backend.jvm.overrides.IrJavaIncompatibilityRulesOverridabilityCondition
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrTypeOperator
import org.jetbrains.kotlin.ir.expressions.IrTypeOperatorCall
import org.jetbrains.kotlin.ir.overrides.IrOverrideChecker
import org.jetbrains.kotlin.ir.overrides.MemberWithOriginal
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.IrTypeSystemContextImpl
import org.jetbrains.kotlin.ir.types.impl.buildSimpleType
import org.jetbrains.kotlin.ir.util.erasedUpperBound
import org.jetbrains.kotlin.ir.util.kotlinFqName
import org.jetbrains.kotlin.ir.util.nonDispatchParameters
import org.jetbrains.kotlin.ir.util.propertyIfAccessor
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.IrTypeTransformerVoid
import org.jetbrains.kotlin.ir.visitors.IrVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.platform.jvm.isJvm
import org.jetbrains.kotlin.resolve.OverridingUtil.OverrideCompatibilityInfo

class RemoveKCastsGenerationExtension : IrGenerationExtension {
  override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
    moduleFragment.acceptChildrenVoid(FixupOverriddenFunctionsVisitor(pluginContext))
    moduleFragment.transformChildrenVoid(RemoveKCastsTransformer())
    moduleFragment.acceptChildrenVoid(EraseKTypesTransformer(pluginContext))
  }
}

private val K_FQNAME = FqName("io.github.kyay10.highkt.K")
private val IrType.isK: Boolean
  get() = erasedUpperBound.kotlinFqName == K_FQNAME
private val IrType.containsK: Boolean
  get() = isK || (this as? IrSimpleType)?.arguments?.any { it is IrType && it.containsK } == true

// Not strictly necessary, but helps clean the code a bit
class RemoveKCastsTransformer : IrElementTransformerVoid() {
  override fun visitTypeOperator(expression: IrTypeOperatorCall): IrExpression {
    if (
      expression.operator == IrTypeOperator.CAST ||
        expression.operator == IrTypeOperator.IMPLICIT_CAST ||
        expression.operator == IrTypeOperator.REINTERPRET_CAST
    ) {
      if (expression.type.isK) {
        return expression.argument.transform(this, null)
      }
    }
    return expression
  }
}

class EraseKTypesTransformer(val pluginContext: IrPluginContext) : IrTypeTransformerVoid() {
  @Suppress("UNCHECKED_CAST")
  override fun <Type : IrType?> transformTypeRecursively(container: IrElement, type: Type): Type =
    when {
      type !is IrSimpleType -> type
      type.isK -> pluginContext.irBuiltIns.anyNType as Type
      else ->
        type.buildSimpleType {
          arguments =
            type.arguments.map {
              if (it !is IrType) return@map it
              transformTypeRecursively(container, it)
            }
        } as Type
    }
}

class FixupOverriddenFunctionsVisitor(pluginContext: IrPluginContext) : IrVisitorVoid() {
  val typeSystemContext =
    if (pluginContext.platform.isJvm()) JvmIrTypeSystemContext(pluginContext.irBuiltIns)
    else IrTypeSystemContextImpl(pluginContext.irBuiltIns)
  val overrideChecker =
    IrOverrideChecker(
      typeSystemContext,
      if (pluginContext.platform.isJvm()) listOf(IrJavaIncompatibilityRulesOverridabilityCondition()) else emptyList(),
    )

  override fun visitElement(element: IrElement) {
    element.acceptChildrenVoid(this)
  }

  @OptIn(UnsafeDuringIrConstructionAPI::class)
  override fun visitClass(declaration: IrClass) {
    for ((_, functions) in declaration.allFunctions.groupBy { it.name }) {
      val fakeOverrides = functions.filter { it.isFakeOverride }
      val realFunctions = functions.filterNot { it.isFakeOverride }
      for (realFunction in realFunctions) {
        var parameterTypes = realFunction.nonDispatchParameters.map { it.type }
        var returnType = realFunction.returnType
        for (fakeOverride in fakeOverrides) {
          if (realFunction.nonDispatchParameters.size != parameterTypes.size) continue
          fakeOverride.nonDispatchParameters.zip(realFunction.nonDispatchParameters).forEach {
            (fakeParameter, realParameter) ->
            if (fakeParameter.type.containsK) { // TODO check that fakeParameter.type evaluates to realParameter.type
              realParameter.type = fakeParameter.type
            }
          }
          if (fakeOverride.returnType.containsK) {
            realFunction.returnType = fakeOverride.returnType
          }
          val overridability =
            overrideChecker.isOverridableBy(
              MemberWithOriginal(fakeOverride),
              MemberWithOriginal(realFunction),
              checkIsInlineFlag = true,
            )
          when (overridability.result) {
            OverrideCompatibilityInfo.Result.OVERRIDABLE -> {
              realFunction.overriddenSymbols += fakeOverride.overriddenSymbols
              parameterTypes = realFunction.nonDispatchParameters.map { it.type }
              returnType = realFunction.returnType
              declaration.declarations.remove(fakeOverride.propertyIfAccessor)
            }

            OverrideCompatibilityInfo.Result.CONFLICT -> Unit
            OverrideCompatibilityInfo.Result.INCOMPATIBLE -> Unit
          }
        }
        realFunction.nonDispatchParameters.zip(parameterTypes).forEach { (parameter, type) -> parameter.type = type }
        realFunction.returnType = returnType
      }
    }
    super.visitClass(declaration)
  }
}

@OptIn(UnsafeDuringIrConstructionAPI::class)
val IrClass.allFunctions: Sequence<IrSimpleFunction>
  get() = declarations.asSequence().flatMap {
    when (it) {
      is IrSimpleFunction -> listOf(it)
      is IrProperty -> listOfNotNull(it.getter, it.setter)
      else -> emptyList()
    }
  }