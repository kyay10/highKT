package io.github.kyay10.highkt.fir

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.SessionHolder
import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.declarations.FirFunction
import org.jetbrains.kotlin.fir.expressions.UnresolvedExpressionTypeAccess
import org.jetbrains.kotlin.fir.expressions.builder.buildArgumentList
import org.jetbrains.kotlin.fir.expressions.builder.buildBlock
import org.jetbrains.kotlin.fir.expressions.builder.buildFunctionCall
import org.jetbrains.kotlin.fir.expressions.builder.buildPropertyAccessExpression
import org.jetbrains.kotlin.fir.expressions.builder.buildThisReceiverExpression
import org.jetbrains.kotlin.fir.expressions.impl.FirSingleExpressionBlock
import org.jetbrains.kotlin.fir.extensions.FirStatusTransformerExtension
import org.jetbrains.kotlin.fir.references.builder.buildExplicitThisReference
import org.jetbrains.kotlin.fir.references.builder.buildResolvedNamedReference
import org.jetbrains.kotlin.fir.references.builder.buildSimpleNamedReference
import org.jetbrains.kotlin.fir.types.FirImplicitTypeRef
import org.jetbrains.kotlin.fir.types.builder.buildTypeProjectionWithVariance
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.fir.types.withReplacedConeType
import org.jetbrains.kotlin.types.Variance

// object AddedRunForFixAll : GeneratedDeclarationKey()

class AddTypeAssertTransformer(session: FirSession) : FirStatusTransformerExtension(session), SessionHolder {
  @OptIn(UnresolvedExpressionTypeAccess::class)
  override fun needTransformStatus(declaration: FirDeclaration): Boolean {
    if (declaration is FirFunction) {
      declaration.body?.let {
        if (it is FirSingleExpressionBlock && declaration.returnTypeRef is FirImplicitTypeRef) {
          // TODO can this be done?
          /* declaration.replaceBody(FirSingleExpressionBlock(buildReturnExpression {
            source = it.statement.source
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
            // TODO handle contracts
            for (valueParam in declaration.contextParameters + declaration.valueParameters) {
              val newType = valueParam.returnTypeRef.coneType.applyKEverywhere() ?: continue
              statements.add(buildFunctionCall {
                coneTypeOrNull = session.builtinTypes.unitType.coneType
                calleeReference = buildSimpleNamedReference {
                  this.name = ASSERT_IS_TYPE
                }
                argumentList = buildArgumentList {
                  arguments.add(buildPropertyAccessExpression {
                    coneTypeOrNull = valueParam.returnTypeRef.coneType
                    calleeReference = buildResolvedNamedReference {
                      this.name = valueParam.name
                      resolvedSymbol = valueParam.symbol
                    }
                  })
                }
                typeArguments += listOf(buildTypeProjectionWithVariance {
                  typeRef = valueParam.returnTypeRef.withReplacedConeType(newType)
                  variance = Variance.INVARIANT
                })
              })
            }
            declaration.receiverParameter?.let { receiverParam ->
              val newType = receiverParam.typeRef.coneType.applyKEverywhere() ?: return@let
              statements.add(buildFunctionCall {
                coneTypeOrNull = session.builtinTypes.unitType.coneType
                calleeReference = buildSimpleNamedReference {
                  this.name = ASSERT_IS_TYPE
                }
                argumentList = buildArgumentList {
                  arguments.add(buildThisReceiverExpression {
                    coneTypeOrNull = receiverParam.typeRef.coneType
                    calleeReference = buildExplicitThisReference {}.apply {
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
            statements.addAll(it.statements)
          })
        }
      }
    }
    return false
  }
}