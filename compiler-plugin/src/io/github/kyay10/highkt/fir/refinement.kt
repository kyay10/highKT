package io.github.kyay10.highkt.fir

import org.jetbrains.kotlin.KtSourceElement
import io.github.kyay10.highkt.fir.KindReturnTypeRefinementExtension.Companion.ASSERT_IS_TYPE
import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.SessionHolder
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
import org.jetbrains.kotlin.fir.expressions.builder.buildFunctionCall
import org.jetbrains.kotlin.fir.expressions.builder.buildPropertyAccessExpression
import org.jetbrains.kotlin.fir.expressions.builder.buildThisReceiverExpression
import org.jetbrains.kotlin.fir.extensions.FirAssignExpressionAltererExtension
import org.jetbrains.kotlin.fir.extensions.FirExtensionApiInternals
import org.jetbrains.kotlin.fir.extensions.FirFunctionCallRefinementExtension
import org.jetbrains.kotlin.fir.references.FirResolvedNamedReference
import org.jetbrains.kotlin.fir.references.builder.buildExplicitThisReference
import org.jetbrains.kotlin.fir.references.builder.buildResolvedNamedReference
import org.jetbrains.kotlin.fir.references.builder.buildSimpleNamedReference
import org.jetbrains.kotlin.fir.resolve.calls.candidate.CallInfo
import org.jetbrains.kotlin.fir.resolve.fullyExpandedType
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.FirFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.builder.buildTypeProjectionWithVariance
import org.jetbrains.kotlin.fir.types.classId
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.fir.types.coneTypeOrNull
import org.jetbrains.kotlin.fir.types.isStarProjection
import org.jetbrains.kotlin.fir.types.replaceType
import org.jetbrains.kotlin.fir.types.resolvedType
import org.jetbrains.kotlin.fir.types.type
import org.jetbrains.kotlin.fir.types.withArguments
import org.jetbrains.kotlin.fir.types.withReplacedConeType
import org.jetbrains.kotlin.fir.visitors.FirVisitorVoid
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.types.Variance

internal object ContainingDeclarationsKey : FirDeclarationDataKey()

internal var FirDeclaration.containingDeclarationsAtInterceptionPoint: Pair<FirNamedFunctionSymbol, List<FirDeclaration>>? by FirDeclarationDataRegistry.data(
  ContainingDeclarationsKey
)

internal object NeedsKRefinement : FirDeclarationDataKey()

internal var FirDeclaration.needsKRefinement: FirNamedFunctionSymbol? by FirDeclarationDataRegistry.data(
  NeedsKRefinement
)

@OptIn(FirExtensionApiInternals::class)
class KindReturnTypeRefinementExtension(session: FirSession) : FirFunctionCallRefinementExtension(session), SessionHolder {
  companion object {
    val PACKAGE_FQN = FqName("io.github.kyay10.highkt")
    val FIX = CallableId(PACKAGE_FQN, callableName = Name.identifier("fix"))
    val ASSERT_IS_TYPE = Name.identifier("assertIsType")
    val K_CLASS_ID = ClassId(PACKAGE_FQN, Name.identifier("K"))
    val K_OUT_CLASS_ID = ClassId(PACKAGE_FQN, Name.identifier("KOut"))
    val K_IN_CLASS_ID = ClassId(PACKAGE_FQN, Name.identifier("KIn"))
    val K_IDS = setOf(K_CLASS_ID, K_OUT_CLASS_ID, K_IN_CLASS_ID)
  }

  @OptIn(UnresolvedExpressionTypeAccess::class, SymbolInternals::class)
  override fun intercept(
    callInfo: CallInfo,
    symbol: FirNamedFunctionSymbol
  ): CallReturnType? {
    if (symbol.callableId == FIX) {
      val declarations = callInfo.containingDeclarations
      return CallReturnType(symbol.resolvedReturnTypeRef) {
        it.fir.containingDeclarationsAtInterceptionPoint = symbol to declarations
      }
    }
    val returnType = symbol.fir.returnTypeRef.coneTypeOrNull
    if (returnType == null || !returnType.needsKApplication()) return null
    return CallReturnType(symbol.resolvedReturnTypeRef) {
      it.fir.needsKRefinement = symbol
    }
  }

  @OptIn(SymbolInternals::class)
  override fun transform(
    call: FirFunctionCall,
    originalSymbol: FirNamedFunctionSymbol
  ): FirFunctionCall {
    call.accept(object: FirVisitorVoid() {
      override fun visitElement(element: FirElement) {
        element.acceptChildren(this)
        if (element is FirFunctionCall) {
          val needsRefinement = (element.calleeReference as? FirResolvedNamedReference)?.resolvedSymbol
            ?.fir?.needsKRefinement
          if (needsRefinement != null) {
            element.replaceCalleeReference(buildResolvedNamedReference {
              name = needsRefinement.name
              source = needsRefinement.source
              resolvedSymbol = needsRefinement
            })
            val newType = element.resolvedType.applyKEverywhere()
              ?: return
            element.replaceConeTypeOrNull(newType)
          }
        }
      }

    })
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

context(_: SessionHolder)
private fun ConeKotlinType.needsKApplication(): Boolean = isK() || typeArguments.any { it.type?.needsKApplication() == true }

context(_: SessionHolder)
private fun ConeKotlinType.isK(): Boolean = fullyExpandedType().classId in KindReturnTypeRefinementExtension.K_IDS

context(_: SessionHolder)
private fun ConeKotlinType.applyKOnTheOutside(): ConeKotlinType? {
  var realType: ConeKotlinType = this
  val appliedTypes = buildList {
    while (realType.isK()) {
      realType = realType.fullyExpandedType()
      val secondArg = realType.typeArguments[1]
      add(0, secondArg)
      realType = realType.typeArguments[0].type ?: return null // Caused by malformed K type
    }
    // Now !realType.isK()
  }
  if (appliedTypes.isEmpty()) return null // implies !this.isK(), so nothing we can do
  if (realType.typeArguments.size != appliedTypes.size) return null
  if (realType.typeArguments.any { !it.isStarProjection }) return null
  return realType.withArguments(appliedTypes.toTypedArray())
}

context(_: SessionHolder)
private fun ConeKotlinType.applyKEverywhere(): ConeKotlinType? {
  if (typeArguments.isEmpty()) return null
  val appliedOutside = applyKOnTheOutside()
  val currentType = appliedOutside ?: this
  var replacedAny = appliedOutside != null
  val replacedTypeArgs = buildList {
    for (arg in currentType.typeArguments) {
      add(arg.type?.applyKEverywhere()?.let {
        replacedAny = true
        arg.replaceType(it)
      } ?: arg)
    }
  }.toTypedArray()
  return if (replacedAny) currentType.withArguments(replacedTypeArgs) else null
}

class FixAllAssignmentAlterer(session: FirSession) : FirAssignExpressionAltererExtension(session), SessionHolder {
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
            val newType = valueParam.returnTypeRef.coneType.applyKEverywhere() ?: return@mapNotNull null
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
            val newType = receiverParam.typeRef.coneType.applyKEverywhere() ?: return@let
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
}