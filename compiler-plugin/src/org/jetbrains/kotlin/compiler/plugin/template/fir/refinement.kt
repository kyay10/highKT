package org.jetbrains.kotlin.compiler.plugin.template.fir

import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.UnresolvedExpressionTypeAccess
import org.jetbrains.kotlin.fir.extensions.FirExtensionApiInternals
import org.jetbrains.kotlin.fir.extensions.FirFunctionCallRefinementExtension
import org.jetbrains.kotlin.fir.references.builder.buildResolvedNamedReference
import org.jetbrains.kotlin.fir.resolve.calls.candidate.CallInfo
import org.jetbrains.kotlin.fir.resolve.fullyExpandedType
import org.jetbrains.kotlin.fir.resolve.providers.symbolProvider
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.FirFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.coneTypeOrNull
import org.jetbrains.kotlin.fir.types.isStarProjection
import org.jetbrains.kotlin.fir.types.resolvedType
import org.jetbrains.kotlin.fir.types.withArguments
import org.jetbrains.kotlin.fir.types.withAttributes
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

@OptIn(FirExtensionApiInternals::class)
class KindReturnTypeRefinementExtension(session: FirSession) : FirFunctionCallRefinementExtension(session) {
  companion object {
    private val PACKAGE_FQN = FqName("org.jetbrains.kotlin.compiler.plugin.template")
    private val FIX_ALL = CallableId(PACKAGE_FQN, callableName = Name.identifier("fixAll"))
    private val ASSERT_IS_TYPE = Name.identifier("assertIsType")
    private val ASSERT_IS_TYPE_ID = CallableId(PACKAGE_FQN, callableName = ASSERT_IS_TYPE)
  }

  @OptIn(UnresolvedExpressionTypeAccess::class, SymbolInternals::class)
  override fun intercept(
    callInfo: CallInfo,
    symbol: FirNamedFunctionSymbol
  ): CallReturnType? {
    /*if (symbol.callableId == FIX_ALL) {
      val assertTypeCalls = callInfo.containingDeclarations.flatMap {
        if(it !is FirFunction) return@flatMap emptyList()
        it.valueParameters.mapNotNull { valueParam ->
          val newType = valueParam.returnTypeRef.coneType.applyKSomewhere(session) ?: return@mapNotNull null
          buildFunctionCall {
            source = callInfo.callSite.source
            coneTypeOrNull = session.builtinTypes.unitType.coneType
            calleeReference = buildResolvedNamedReference {
              name = ASSERT_IS_TYPE
              resolvedSymbol = assertIsTypeSymbol
              source = callInfo.callSite.source
            }
            argumentList = buildArgumentListCopy(callInfo.argumentList) {
              arguments.add(buildPropertyAccessExpression {
                coneTypeOrNull = valueParam.returnTypeRef.coneType
                calleeReference = buildResolvedNamedReference {
                  name = valueParam.name
                  resolvedSymbol = valueParam.symbol
                }
              })
            }
            typeArguments += listOf(buildTypeProjectionWithVariance {
              typeRef = valueParam.returnTypeRef.withReplacedConeType(newType)
              variance = Variance.INVARIANT
            })
          }
        }
      }
      //(callInfo.argumentList.arguments as MutableList).addAll(assertTypeCalls)
      return null
    }*/
    (callInfo.arguments + callInfo.explicitReceiver).forEach {
      it ?: return@forEach
      val newType = it.coneTypeOrNull?.applyKSomewhere(session) ?: return@forEach
      it.replaceConeTypeOrNull(newType)
    }
    val returnType = symbol.fir.returnTypeRef.coneTypeOrNull
    returnType?.attributes?.kind ?: return null
    return CallReturnType(symbol.resolvedReturnTypeRef) {

    }
  }

  override fun transform(
    call: FirFunctionCall,
    originalSymbol: FirNamedFunctionSymbol
  ): FirFunctionCall {
    call.replaceCalleeReference(buildResolvedNamedReference {
      name = originalSymbol.name
      source = originalSymbol.source
      resolvedSymbol = originalSymbol
    })
    val returnType = call.resolvedType.fullyExpandedType(session)
    val newType = returnType.applyKSomewhere(session) ?: return call
    call.replaceConeTypeOrNull(newType)
    return call
  }

  override fun anchorElement(symbol: FirRegularClassSymbol): KtSourceElement {
    TODO("Not yet implemented")
  }

  override fun ownsSymbol(symbol: FirRegularClassSymbol) = false

  override fun restoreSymbol(
    call: FirFunctionCall,
    name: Name
  ) = null

  val assertIsTypeSymbol by lazy {
    session.symbolProvider.getTopLevelFunctionSymbols(PACKAGE_FQN, ASSERT_IS_TYPE).first() as FirFunctionSymbol<*>
  }
}

private fun ConeKotlinType.applyKOnce(): ConeKotlinType? {
  val kind = attributes.kind ?: return null
  val firstStar = typeArguments.withIndex().indexOfFirst { it.value.isStarProjection }
  if (firstStar == -1) return null
  return withArguments(typeArguments.toMutableList().apply {
    this[firstStar] = kind.coneType
  }.toTypedArray()).withAttributes(attributes.remove(kind))
}

private fun ConeKotlinType.applyKSomewhere(session: FirSession): ConeKotlinType? {
  applyKOnce()?.let { return it.applyKSomewhere(session) ?: it }
  if (typeArguments.isEmpty()) return null
  var replacedAny = false
  val replacedTypeArgs = buildList {
    for (arg in typeArguments) {
      val replaced = (arg as? ConeKotlinType)?.applyKSomewhere(session)
      if (replaced != null) {
        replacedAny = true
      }
      add(replaced ?: arg)
    }
  }.toTypedArray()
  return if (replacedAny) withArguments(replacedTypeArgs) else null
}