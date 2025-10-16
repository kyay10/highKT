package io.github.kyay10.highkt.fir

import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.SessionHolder
import org.jetbrains.kotlin.fir.declarations.FirAnonymousFunction
import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.declarations.FirFunction
import org.jetbrains.kotlin.fir.declarations.FirProperty
import org.jetbrains.kotlin.fir.declarations.FirReceiverParameter
import org.jetbrains.kotlin.fir.declarations.FirVariable
import org.jetbrains.kotlin.fir.declarations.utils.isDelegatedProperty
import org.jetbrains.kotlin.fir.declarations.utils.isInline
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.FirLazyBlock
import org.jetbrains.kotlin.fir.expressions.FirPropertyAccessExpression
import org.jetbrains.kotlin.fir.expressions.FirStatement
import org.jetbrains.kotlin.fir.expressions.FirVariableAssignment
import org.jetbrains.kotlin.fir.expressions.UnresolvedExpressionTypeAccess
import org.jetbrains.kotlin.fir.expressions.arguments
import org.jetbrains.kotlin.fir.expressions.builder.FirFunctionCallBuilder
import org.jetbrains.kotlin.fir.expressions.builder.buildArgumentList
import org.jetbrains.kotlin.fir.expressions.builder.buildBlock
import org.jetbrains.kotlin.fir.expressions.builder.buildFunctionCall
import org.jetbrains.kotlin.fir.expressions.builder.buildFunctionCallCopy
import org.jetbrains.kotlin.fir.expressions.builder.buildPropertyAccessExpression
import org.jetbrains.kotlin.fir.expressions.builder.buildResolvedQualifier
import org.jetbrains.kotlin.fir.expressions.builder.buildThisReceiverExpression
import org.jetbrains.kotlin.fir.expressions.builder.buildVariableAssignment
import org.jetbrains.kotlin.fir.expressions.impl.FirContractCallBlock
import org.jetbrains.kotlin.fir.expressions.impl.FirEmptyExpressionBlock
import org.jetbrains.kotlin.fir.expressions.impl.FirSingleExpressionBlock
import org.jetbrains.kotlin.fir.extensions.FirAssignExpressionAltererExtension
import org.jetbrains.kotlin.fir.extensions.FirExtensionApiInternals
import org.jetbrains.kotlin.fir.extensions.FirStatusTransformerExtension
import org.jetbrains.kotlin.fir.packageFqName
import org.jetbrains.kotlin.fir.references.builder.buildExplicitThisReference
import org.jetbrains.kotlin.fir.references.builder.buildResolvedNamedReference
import org.jetbrains.kotlin.fir.references.builder.buildSimpleNamedReference
import org.jetbrains.kotlin.fir.references.resolved
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol
import org.jetbrains.kotlin.fir.types.FirImplicitTypeRef
import org.jetbrains.kotlin.fir.types.builder.buildTypeProjectionWithVariance
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.fir.types.coneTypeOrNull
import org.jetbrains.kotlin.fir.types.isNonReflectFunctionType
import org.jetbrains.kotlin.fir.types.toRegularClassSymbol
import org.jetbrains.kotlin.fir.types.withReplacedConeType
import org.jetbrains.kotlin.fir.visitors.FirVisitorVoid
import org.jetbrains.kotlin.types.Variance

// object AddedRunForFixAll : GeneratedDeclarationKey()

class AddTypeAssertTransformer(session: FirSession) : FirStatusTransformerExtension(session), SessionHolder {
  @OptIn(UnresolvedExpressionTypeAccess::class)
  override fun needTransformStatus(declaration: FirDeclaration): Boolean {
    val body = (declaration as? FirFunction)?.body
    if (body != null && body !is FirEmptyExpressionBlock && body !is FirLazyBlock && body.statements.isNotEmpty()) {
      if (body is FirSingleExpressionBlock && declaration.returnTypeRef is FirImplicitTypeRef) {
        // TODO can this be done?
        /* declaration.replaceBody(FirSingleExpressionBlock(buildReturnExpression {
          source = body.statement.source
          target = FirFunctionTarget(labelName = null, isLambda = false).apply {
            bind(declaration)
          }
          result = buildFunctionCall {
            calleeReference = buildSimpleNamedReference {
              name = Name.identifier("run")
            }
            explicitReceiver = buildPropertyAccessExpression {
              calleeReference = buildSimpleNamedReference {
                name = KOTLIN_NAME
              }
            }
            argumentList = buildArgumentList {
              arguments.add(buildAnonymousFunctionExpression {
                anonymousFunction = buildAnonymousFunction {
                  moduleData = declaration.moduleData
                  origin = FirDeclarationOrigin.Plugin(AddedRunForFixAll)
                  returnTypeRef = session.builtinTypes.nothingType
                  symbol = FirAnonymousFunctionSymbol()
                  isLambda = true
                  hasExplicitParameterList = false
                  body = TODO()
                }
              })
            }
          }
        })) */
      } else {
        declaration.replaceBody(buildBlock {
          coneTypeOrNull = body.coneTypeOrNull
          for (valueParam in declaration.contextParameters + declaration.valueParameters) {
            // TODO handle untyped lambda parameters
            if (valueParam.returnTypeRef.coneTypeOrNull?.isNonReflectFunctionType(session) == true && declaration.isInline && !valueParam.isNoinline) continue
            val newType = valueParam.returnTypeRef.coneTypeOrNull?.applyKAndCanonicalizeOrNull() ?: continue
            statements.add(valueParam.buildTypeAssertCall {
              typeArguments += listOf(buildTypeProjectionWithVariance {
                source = valueParam.source
                typeRef = valueParam.returnTypeRef.withReplacedConeType(newType)
                variance = Variance.INVARIANT
              })
            })
          }
          declaration.receiverParameter?.let { receiverParam ->
            // TODO handle untyped lambda receivers
            val newType = receiverParam.typeRef.coneTypeOrNull?.applyKAndCanonicalizeOrNull() ?: return@let
            statements.add(receiverParam.buildTypeAssertCall {
              typeArguments += listOf(buildTypeProjectionWithVariance {
                source = receiverParam.source
                typeRef = receiverParam.typeRef.withReplacedConeType(newType)
                variance = Variance.INVARIANT
              })
            })
          }
          if (body.statements.first() is FirContractCallBlock) {
            statements.add(0, body.statements.first())
            statements.addAll(body.statements.drop(1))
          } else {
            statements.addAll(body.statements)
          }
          val iterator = statements.listIterator()
          for (statement in iterator) {
            if (statement is FirProperty && statement.initializer != null && !statement.isDelegatedProperty && !statement.name.isSpecial) {
              val propertyType = statement.returnTypeRef.coneTypeOrNull
              val newType = propertyType?.applyKAndCanonicalizeOrNull()
              if (newType != null) iterator.add(statement.buildTypeAssertCall {
                typeArguments += listOf(buildTypeProjectionWithVariance {
                  source = statement.source
                  typeRef = statement.returnTypeRef.withReplacedConeType(newType)
                  variance = Variance.INVARIANT
                })
              })
              else if (propertyType == null) iterator.add(buildVariableAssignment {
                source = statement.source
                lValue = buildPropertyAccessExpression {
                  source = statement.source
                  calleeReference = buildResolvedNamedReference {
                    source = statement.source
                    name = statement.name
                    resolvedSymbol = statement.symbol
                  }
                }
                rValue = statement.buildTypeAssertCall { }
              })
            }
          }
        })
      }
      declaration.acceptChildren(object : FirVisitorVoid() {
        override fun visitElement(element: FirElement) {
          element.acceptChildren(this)
        }

        override fun visitAnonymousFunction(anonymousFunction: FirAnonymousFunction) {
          needTransformStatus(anonymousFunction)
        }

        override fun visitFunction(function: FirFunction) {} // Don't go into nested named functions
      })
    }
    return false
  }
}

@OptIn(FirExtensionApiInternals::class)
class AddTypeToTypeAssertTransformer(session: FirSession) : FirAssignExpressionAltererExtension(session),
  SessionHolder {
  override fun transformVariableAssignment(variableAssignment: FirVariableAssignment): FirStatement? {
    val typeAssertion = variableAssignment.rValue
    if (typeAssertion !is FirFunctionCall || typeAssertion.calleeReference.name != ASSERT_IS_TYPE) return null
    val property =
      (typeAssertion.arguments.single() as FirPropertyAccessExpression).calleeReference.resolved!!.resolvedSymbol as FirPropertySymbol
    val newType = property.resolvedReturnType.applyKAndCanonicalizeOrNull() ?: return buildResolvedQualifier {
      symbol = session.builtinTypes.unitType.toRegularClassSymbol(session)
      coneTypeOrNull = session.builtinTypes.unitType.coneType
      packageFqName = symbol!!.packageFqName()
      relativeClassFqName = symbol!!.classId.relativeClassName
      resolvedToCompanionObject = false
    }
    return buildFunctionCallCopy(typeAssertion) {
      typeArguments += listOf(buildTypeProjectionWithVariance {
        source = typeAssertion.source
        typeRef = property.resolvedReturnTypeRef.withReplacedConeType(newType)
        variance = Variance.INVARIANT
      })
    }
  }
}

context(c: SessionHolder)
private inline fun FirReceiverParameter.buildTypeAssertCall(
  block: FirFunctionCallBuilder.() -> Unit
): FirFunctionCall = buildTypeAssertCallBasic {
  argumentList = buildArgumentList {
    source = this@buildTypeAssertCall.source
    arguments.add(buildThisReceiverExpression {
      source = this@buildTypeAssertCall.source
      coneTypeOrNull = typeRef.coneType
      calleeReference = buildExplicitThisReference {
        source = this@buildTypeAssertCall.source
      }.apply {
        replaceBoundSymbol(symbol)
      }
    })
  }
  block()
}

context(c: SessionHolder)
private inline fun FirVariable.buildTypeAssertCall(
  block: FirFunctionCallBuilder.() -> Unit
): FirFunctionCall = buildTypeAssertCallBasic {
  argumentList = buildArgumentList {
    source = this@buildTypeAssertCall.source
    arguments.add(buildPropertyAccessExpression {
      source = this@buildTypeAssertCall.source
      coneTypeOrNull = returnTypeRef.coneTypeOrNull
      calleeReference = buildResolvedNamedReference {
        source = this@buildTypeAssertCall.source
        name = this@buildTypeAssertCall.name
        resolvedSymbol = symbol
      }
    })
  }
  block()
}

context(c: SessionHolder)
private inline fun FirElement.buildTypeAssertCallBasic(
  block: FirFunctionCallBuilder.() -> Unit
): FirFunctionCall = buildFunctionCall {
  source = this@buildTypeAssertCallBasic.source
  coneTypeOrNull = c.session.builtinTypes.unitType.coneType
  calleeReference = buildSimpleNamedReference {
    source = this@buildTypeAssertCallBasic.source
    name = ASSERT_IS_TYPE
  }
  explicitReceiver = PACKAGE_FQN.pathSegments().fold(null) { acc, name ->
    buildPropertyAccessExpression {
      source = this@buildTypeAssertCallBasic.source
      calleeReference = buildSimpleNamedReference {
        source = this@buildTypeAssertCallBasic.source
        this.name = name
      }
      explicitReceiver = acc
    }
  }
  block()
}