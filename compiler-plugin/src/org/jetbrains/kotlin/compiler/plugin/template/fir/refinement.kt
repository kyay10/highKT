package org.jetbrains.kotlin.compiler.plugin.template.fir

import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.compiler.plugin.template.fir.KindReturnTypeRefinementExtension.Companion.ASSERT_IS_TYPE
import org.jetbrains.kotlin.compiler.plugin.template.fir.KindReturnTypeRefinementExtension.Companion.PACKAGE_FQN
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.declarations.FirDeclarationDataKey
import org.jetbrains.kotlin.fir.declarations.FirDeclarationDataRegistry
import org.jetbrains.kotlin.fir.declarations.FirFunction
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.FirPropertyAccessExpression
import org.jetbrains.kotlin.fir.expressions.FirStatement
import org.jetbrains.kotlin.fir.expressions.FirVariableAssignment
import org.jetbrains.kotlin.fir.expressions.UnresolvedExpressionTypeAccess
import org.jetbrains.kotlin.fir.expressions.builder.buildArgumentList
import org.jetbrains.kotlin.fir.expressions.builder.buildArgumentListCopy
import org.jetbrains.kotlin.fir.expressions.builder.buildFunctionCall
import org.jetbrains.kotlin.fir.expressions.builder.buildFunctionCallCopy
import org.jetbrains.kotlin.fir.expressions.builder.buildPropertyAccessExpression
import org.jetbrains.kotlin.fir.expressions.builder.buildThisReceiverExpression
import org.jetbrains.kotlin.fir.extensions.FirAssignExpressionAltererExtension
import org.jetbrains.kotlin.fir.extensions.FirExtensionApiInternals
import org.jetbrains.kotlin.fir.extensions.FirFunctionCallRefinementExtension
import org.jetbrains.kotlin.fir.references.FirResolvedNamedReference
import org.jetbrains.kotlin.fir.references.builder.buildExplicitThisReference
import org.jetbrains.kotlin.fir.references.builder.buildImplicitThisReference
import org.jetbrains.kotlin.fir.references.builder.buildResolvedNamedReference
import org.jetbrains.kotlin.fir.references.builder.buildSimpleNamedReference
import org.jetbrains.kotlin.fir.resolve.calls.candidate.CallInfo
import org.jetbrains.kotlin.fir.resolve.fullyExpandedType
import org.jetbrains.kotlin.fir.resolve.providers.symbolProvider
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.FirFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.builder.buildTypeProjectionWithVariance
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.fir.types.coneTypeOrNull
import org.jetbrains.kotlin.fir.types.isStarProjection
import org.jetbrains.kotlin.fir.types.resolvedType
import org.jetbrains.kotlin.fir.types.withArguments
import org.jetbrains.kotlin.fir.types.withAttributes
import org.jetbrains.kotlin.fir.types.withReplacedConeType
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.types.Variance

internal object ContainingDeclarationsKey : FirDeclarationDataKey()

internal var FirDeclaration.containingDeclarationsAtInterceptionPoint: Pair<FirNamedFunctionSymbol, List<FirDeclaration>>? by FirDeclarationDataRegistry.data(
  ContainingDeclarationsKey
)


@OptIn(FirExtensionApiInternals::class)
class KindReturnTypeRefinementExtension(session: FirSession) : FirFunctionCallRefinementExtension(session) {
  companion object {
    val PACKAGE_FQN = FqName("org.jetbrains.kotlin.compiler.plugin.template")
    val FIX_ALL = CallableId(PACKAGE_FQN, callableName = Name.identifier("fixAll"))
    val ASSERT_IS_TYPE = Name.identifier("assertIsType")
  }

  @OptIn(UnresolvedExpressionTypeAccess::class, SymbolInternals::class)
  override fun intercept(
    callInfo: CallInfo,
    symbol: FirNamedFunctionSymbol
  ): CallReturnType? {
    if (symbol.callableId == FIX_ALL) {
      val declarations = callInfo.containingDeclarations
      return CallReturnType(symbol.resolvedReturnTypeRef) {
        it.fir.containingDeclarationsAtInterceptionPoint = symbol to declarations
      }
    }
    val returnType = symbol.fir.returnTypeRef.coneTypeOrNull
    returnType?.attributes?.kind ?: return null
    return CallReturnType(symbol.resolvedReturnTypeRef) {}
  }

  override fun transform(
    call: FirFunctionCall,
    originalSymbol: FirNamedFunctionSymbol
  ): FirFunctionCall {
    if (originalSymbol.callableId == FIX_ALL) {
      // will be handled in assignment alterer
      return call
    }
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

class FixAllAssignmentAlterer(session: FirSession) : FirAssignExpressionAltererExtension(session) {
  @OptIn(SymbolInternals::class)
  override fun transformVariableAssignment(variableAssignment: FirVariableAssignment): FirStatement? {
    val fixAllCall = (variableAssignment.lValue as? FirPropertyAccessExpression)?.explicitReceiver as? FirFunctionCall
      ?: return null
    val (originalSymbol, containingDeclarations) =
      ((fixAllCall.calleeReference as? FirResolvedNamedReference)?.resolvedSymbol as? FirFunctionSymbol<*>)
        ?.fir?.containingDeclarationsAtInterceptionPoint
        ?: return null
    return buildFunctionCall {
      coneTypeOrNull = variableAssignment.lValue.resolvedType
      calleeReference = buildSimpleNamedReference {
        name = originalSymbol.name
      }
      argumentList = buildArgumentList {
        containingDeclarations.forEach {
          if (it !is FirFunction) return@forEach
          arguments.addAll((it.contextParameters + it.valueParameters).mapNotNull { valueParam ->
            val newType = valueParam.returnTypeRef.coneType.applyKSomewhere(session) ?: return@mapNotNull null
            buildFunctionCall {
              coneTypeOrNull = session.builtinTypes.unitType.coneType
              calleeReference = buildSimpleNamedReference {
                name = ASSERT_IS_TYPE
              }
              argumentList = buildArgumentList {
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
          })
          it.receiverParameter?.let { receiverParam ->
            val newType = receiverParam.typeRef.coneType.applyKSomewhere(session) ?: return@let
            arguments.add(buildFunctionCall {
              coneTypeOrNull = session.builtinTypes.unitType.coneType
              calleeReference = buildSimpleNamedReference {
                name = ASSERT_IS_TYPE
              }
              argumentList = buildArgumentList {
                arguments.add(buildThisReceiverExpression {
                  coneTypeOrNull = receiverParam.typeRef.coneType
                  calleeReference = buildExplicitThisReference {
                  }.apply {
                    replaceBoundSymbol(receiverParam.symbol)
                  }
                })
              }
              typeArguments += listOf(buildTypeProjectionWithVariance {
                typeRef = receiverParam.typeRef.withReplacedConeType(newType)
                variance = Variance.INVARIANT
              })
            })
          }
        }
      }
    }
  }

  val assertIsTypeSymbol by lazy {
    session.symbolProvider.getTopLevelFunctionSymbols(PACKAGE_FQN, ASSERT_IS_TYPE).first() as FirFunctionSymbol<*>
  }
}