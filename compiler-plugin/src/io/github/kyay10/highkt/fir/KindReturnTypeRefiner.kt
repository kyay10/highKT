package io.github.kyay10.highkt.fir

import arrow.core.raise.merge
import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.SessionAndScopeSessionHolder
import org.jetbrains.kotlin.fir.SessionHolder
import org.jetbrains.kotlin.fir.expressions.FirAnonymousFunctionExpression
import org.jetbrains.kotlin.fir.expressions.FirBlock
import org.jetbrains.kotlin.fir.expressions.FirCatch
import org.jetbrains.kotlin.fir.expressions.FirElvisExpression
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.FirImplicitInvokeCall
import org.jetbrains.kotlin.fir.expressions.FirNamedArgumentExpression
import org.jetbrains.kotlin.fir.expressions.FirReturnExpression
import org.jetbrains.kotlin.fir.expressions.FirSafeCallExpression
import org.jetbrains.kotlin.fir.expressions.FirTryExpression
import org.jetbrains.kotlin.fir.expressions.FirVariableAssignment
import org.jetbrains.kotlin.fir.expressions.FirWhenBranch
import org.jetbrains.kotlin.fir.expressions.FirWhenExpression
import org.jetbrains.kotlin.fir.expressions.UnresolvedExpressionTypeAccess
import org.jetbrains.kotlin.fir.expressions.arguments
import org.jetbrains.kotlin.fir.extensions.FirExpressionResolutionExtension
import org.jetbrains.kotlin.fir.extensions.FirExtensionApiInternals
import org.jetbrains.kotlin.fir.extensions.FirFunctionCallRefinementExtension
import org.jetbrains.kotlin.fir.references.resolved
import org.jetbrains.kotlin.fir.references.symbol
import org.jetbrains.kotlin.fir.references.toResolvedCallableSymbol
import org.jetbrains.kotlin.fir.resolve.calls.ConeResolvedLambdaAtom
import org.jetbrains.kotlin.fir.resolve.calls.ImplicitExtensionReceiverValue
import org.jetbrains.kotlin.fir.resolve.calls.candidate.CallInfo
import org.jetbrains.kotlin.fir.resolve.calls.candidate.FirNamedReferenceWithCandidate
import org.jetbrains.kotlin.fir.resolve.transformers.body.resolve.FirAbstractBodyResolveTransformer
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.ConeTypeIntersector
import org.jetbrains.kotlin.fir.types.ConeTypeVariableType
import org.jetbrains.kotlin.fir.types.coneTypeOrNull
import org.jetbrains.kotlin.fir.types.typeContext
import org.jetbrains.kotlin.fir.types.withReplacedReturnType
import org.jetbrains.kotlin.fir.visitors.FirDefaultVisitor
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.resolve.calls.inference.model.ConstraintKind
import org.jetbrains.kotlin.types.AbstractTypeChecker

class KindReturnTypeRefiner(session: FirSession) : FirExpressionResolutionExtension(session), SessionHolder {
  override fun addNewImplicitReceivers(
    functionCall: FirFunctionCall,
    sessionHolder: SessionAndScopeSessionHolder,
    containingCallableSymbol: FirCallableSymbol<*>
  ) = addNewImplicitReceivers(functionCall, sessionHolder, containingCallableSymbol as FirBasedSymbol<*>)

  @OptIn(SymbolInternals::class)
  fun addNewImplicitReceivers(
    functionCall: FirFunctionCall,
    sessionHolder: SessionAndScopeSessionHolder,
    containingCallableSymbol: FirBasedSymbol<*>
  ): List<ImplicitExtensionReceiverValue> {
    sessionHolder as FirAbstractBodyResolveTransformer.BodyResolveTransformerComponents
    if (functionCall.calleeReference.resolved?.toResolvedCallableSymbol()?.callableId != EXPAND_TO_ID) {
      val expectedType = functionCall.expectedType(sessionHolder.file.symbol)
      functionCall.transformConeType {
        val newType = it.applyKOrSelf()
        if (expectedType != null && expectedType.isK() && newType expandsTo expectedType) expectedType
        else newType.canonicalize(expectedType.isK())
      }
    }
    return emptyList()
  }
}

@OptIn(FirExtensionApiInternals::class)
class KindExpectedTypeCanonicalizer(session: FirSession) : FirFunctionCallRefinementExtension(session), SessionHolder {
  override fun anchorElement(symbol: FirRegularClassSymbol): KtSourceElement {
    error("unreachable")
  }

  @OptIn(UnresolvedExpressionTypeAccess::class)
  override fun intercept(
    callInfo: CallInfo,
    symbol: FirNamedFunctionSymbol
  ): CallReturnType? {
    // TODO restore original type upon successive calls
    val valueParameterSymbols = symbol.valueParameterSymbols
    val receiverType = symbol.resolvedReceiverType ?: symbol.dispatchReceiverType
    val indexOfLastPositionalArgument = callInfo.arguments.indexOfLast { it !is FirNamedArgumentExpression }
    val positionalArguments = callInfo.arguments.subList(0, indexOfLastPositionalArgument + 1)

    @Suppress("UNCHECKED_CAST")
    val namedArguments = callInfo.arguments.subList(
      indexOfLastPositionalArgument + 1,
      callInfo.arguments.size
    ) as List<FirNamedArgumentExpression>
    for (namedArgument in namedArguments) {
      val expectedType =
        valueParameterSymbols.find { it.name == namedArgument.name }?.resolvedReturnType ?: continue
      namedArgument.expression.transformConeType { it.canonicalize(expectedType.isK()) }
    }
    val parameterIterator = valueParameterSymbols.listIterator()
    for (argument in positionalArguments) {
      if (!parameterIterator.hasPrevious() || !parameterIterator.previous().isVararg) {
        if (!parameterIterator.hasNext()) break
        parameterIterator.next()
      }
      (if (argument is FirNamedArgumentExpression) argument.expression else argument).transformConeType {
        it.canonicalize(parameterIterator.previous().resolvedReturnType.isK())
      }
    }
    callInfo.explicitReceiver?.transformConeType { it.canonicalize(receiverType.isK()) }
    return null
  }

  override fun ownsSymbol(symbol: FirRegularClassSymbol): Boolean {
    error("unreachable")
  }

  override fun restoreSymbol(
    call: FirFunctionCall,
    name: Name
  ): FirRegularClassSymbol? {
    error("unreachable")
  }

  override fun transform(
    call: FirFunctionCall,
    originalSymbol: FirNamedFunctionSymbol
  ): FirFunctionCall {
    error("unreachable")
  }
}

@OptIn(SymbolInternals::class)
context(c: SessionHolder)
private fun FirFunctionCall.expectedType(containingCallableSymbol: FirBasedSymbol<*>): ConeKotlinType? = merge {
  containingCallableSymbol.fir.accept(object : FirDefaultVisitor<Unit, ConeKotlinType?>() {
    override fun visitElement(
      element: FirElement,
      data: ConeKotlinType?
    ) {
      if (element is FirExpression && matches(element)) {
        raise(data)
      }
      if (element is FirFunctionCall) {
        val calleeReference = element.calleeReference
        if (calleeReference is FirNamedReferenceWithCandidate) {
          element.explicitReceiver?.accept(this, null)
          element.arguments.forEach { arg ->
            val atom =
              calleeReference.candidate.postponedAtoms.find { it.expression == arg } ?: return@forEach arg.accept(
                this,
                null
              )
            val previousFlag = AbstractTypeChecker.RUN_SLOW_ASSERTIONS
            AbstractTypeChecker.RUN_SLOW_ASSERTIONS = false
            if (atom is ConeResolvedLambdaAtom) {
              val returnType = atom.returnType
              atom.expression.anonymousFunction.body?.accept(
                this,
                if (returnType is ConeTypeVariableType) calleeReference.candidate.system.notFixedTypeVariables[returnType.typeConstructor]?.let {
                  val uppers = it.constraints.filter { it.kind == ConstraintKind.UPPER }.mapNotNull { it.type as? ConeKotlinType }
                  if (uppers.isNotEmpty()) ConeTypeIntersector.intersectTypes(c.session.typeContext, uppers) else null
                } ?: returnType else returnType)
            } else arg.accept(this, atom.expectedType) // TODO untested
            AbstractTypeChecker.RUN_SLOW_ASSERTIONS = previousFlag
            return
          }
        }
      }
      element.acceptChildren(this, null)
    }

    override fun visitReturnExpression(
      returnExpression: FirReturnExpression,
      data: ConeKotlinType?
    ) {
      returnExpression.acceptChildren(this, returnExpression.target.labeledElement.returnTypeRef.coneTypeOrNull)
    }

    override fun visitBlock(
      block: FirBlock,
      data: ConeKotlinType?
    ) {
      val statements = block.statements.ifEmpty { return }
      for (statement in statements.dropLast(1)) {
        statement.accept(this, null)
      }
      statements.last().accept(this, data)
    }

    override fun visitWhenExpression(
      whenExpression: FirWhenExpression,
      data: ConeKotlinType?
    ) {
      whenExpression.calleeReference.accept(this, null)
      whenExpression.subjectVariable?.accept(this, null)
      whenExpression.branches.forEach { it.accept(this, data) }
    }

    override fun visitWhenBranch(
      whenBranch: FirWhenBranch,
      data: ConeKotlinType?
    ) {
      whenBranch.condition.accept(this, null)
      whenBranch.result.accept(this, data)
    }

    override fun visitTryExpression(
      tryExpression: FirTryExpression,
      data: ConeKotlinType?
    ) {
      tryExpression.calleeReference.accept(this, null)
      tryExpression.tryBlock.accept(this, data)
      tryExpression.catches.forEach { it.accept(this, data) }
      tryExpression.finallyBlock?.accept(this, data)
    }

    override fun visitCatch(
      catch: FirCatch,
      data: ConeKotlinType?
    ) {
      catch.parameter.accept(this, null)
      catch.block.accept(this, data)
    }

    override fun visitElvisExpression(
      elvisExpression: FirElvisExpression,
      data: ConeKotlinType?
    ) {
      elvisExpression.calleeReference
      elvisExpression.lhs.accept(this, data)
      elvisExpression.rhs.accept(this, data)
    }

    override fun visitSafeCallExpression(
      safeCallExpression: FirSafeCallExpression,
      data: ConeKotlinType?
    ) {
      safeCallExpression.receiver.accept(this, null)
      safeCallExpression.selector.accept(this, data)
    }

    override fun visitVariableAssignment(
      variableAssignment: FirVariableAssignment,
      data: ConeKotlinType?
    ) {
      variableAssignment.lValue.accept(this, null)
      variableAssignment.rValue.accept(this, data)
    }
  }, null)
  null
}

@OptIn(UnresolvedExpressionTypeAccess::class)
private fun FirFunctionCall.matches(expression: FirExpression?): Boolean {
  if (this === expression) return true
  if (expression !is FirFunctionCall) return false
  if (this !is FirImplicitInvokeCall) return false
  if (expression.coneTypeOrNull != null) return false
  return expression.source == this.source && expression.calleeReference.symbol == this.calleeReference.symbol
}

@OptIn(UnresolvedExpressionTypeAccess::class)
private tailrec fun FirExpression.transformConeType(transform: (ConeKotlinType) -> ConeKotlinType) {
  if (this is FirNamedArgumentExpression) return expression.transformConeType(transform)
  if (this is FirAnonymousFunctionExpression) {
    anonymousFunction.replaceReturnTypeRef(
      anonymousFunction.returnTypeRef.withReplacedReturnType(
        anonymousFunction.returnTypeRef.coneTypeOrNull?.let(
          transform
        )
      )
    )
    return
  }
  replaceConeTypeOrNull(coneTypeOrNull?.let(transform))
}