package io.github.kyay10.highkt.fir

import arrow.core.raise.merge
import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.SessionAndScopeSessionHolder
import org.jetbrains.kotlin.fir.SessionHolder
import org.jetbrains.kotlin.fir.declarations.FirProperty
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.FirImplicitInvokeCall
import org.jetbrains.kotlin.fir.expressions.UnresolvedExpressionTypeAccess
import org.jetbrains.kotlin.fir.extensions.FirExpressionResolutionExtension
import org.jetbrains.kotlin.fir.references.resolved
import org.jetbrains.kotlin.fir.references.symbol
import org.jetbrains.kotlin.fir.references.toResolvedCallableSymbol
import org.jetbrains.kotlin.fir.resolve.calls.ImplicitExtensionReceiverValue
import org.jetbrains.kotlin.fir.resolve.calls.candidate.FirNamedReferenceWithCandidate
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.resolvedType
import org.jetbrains.kotlin.fir.visitors.FirDefaultVisitorVoid
import org.jetbrains.kotlin.resolve.calls.inference.model.Constraint
import org.jetbrains.kotlin.resolve.calls.inference.model.ConstraintKind
import org.jetbrains.kotlin.resolve.calls.inference.model.IncorporationConstraintPosition
import org.jetbrains.kotlin.resolve.calls.inference.model.MutableVariableWithConstraints
import org.jetbrains.kotlin.resolve.calls.inference.model.OnlyInputTypeConstraintPosition
import org.jetbrains.kotlin.resolve.calls.inference.model.VariableWithConstraints
import org.jetbrains.kotlin.types.model.KotlinTypeMarker
import org.jetbrains.kotlin.types.model.TypeConstructorMarker
import org.jetbrains.kotlin.types.model.TypeVariableMarker

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
    if (functionCall.calleeReference.resolved?.toResolvedCallableSymbol()?.callableId != EXPAND_TO_ID) {
      val type = functionCall.resolvedType
      val shouldCanonicalize = !functionCall.isPropertyInitializer(containingCallableSymbol)
      functionCall.replaceConeTypeOrNull(
        if (shouldCanonicalize) type.applyKAndCanonicalize() else type.applyKEverywhere() ?: type
      )
      (functionCall.calleeReference as? FirNamedReferenceWithCandidate)?.candidate?.system?.let { system ->
        for (entry in system.notFixedTypeVariables) {
          val (_, constraints) = entry
          var replacedAny = false
          val newConstraints = constraints.constraints.map {
            val type = it.type as? ConeKotlinType
            val newType = type?.applyKEverywhere()
            if (newType != null && newType != type) {
              replacedAny = true
              it.copy(type = newType)
            } else it
          }
          if (replacedAny) {
            entry.setValue(MutableVariableWithConstraints(system, object : VariableWithConstraints {
              override val typeVariable: TypeVariableMarker
                get() = constraints.typeVariable
              override val constraints: List<Constraint>
                get() = newConstraints

              override fun getConstraintsContainedSpecifiedTypeVariable(typeVariableConstructor: TypeConstructorMarker): Collection<Constraint> {
                return emptyList()
              }
            }))
          }
        }
      }
    }
    return emptyList()
  }
}

@OptIn(SymbolInternals::class)
private fun FirFunctionCall.isPropertyInitializer(containingCallableSymbol: FirBasedSymbol<*>): Boolean = merge {
  containingCallableSymbol.fir.accept(object : FirDefaultVisitorVoid() {
    override fun visitElement(element: FirElement) {
      if (element is FirProperty)
        if (element.initializer === this@isPropertyInitializer || isImplicitInvokeCall(element.initializer)) raise(
          true
        )
      element.acceptChildren(this)
    }
  })
  false
}

@OptIn(UnresolvedExpressionTypeAccess::class)
private fun FirFunctionCall.isImplicitInvokeCall(call: FirExpression?): Boolean {
  if (call !is FirFunctionCall) return false
  if (this !is FirImplicitInvokeCall) return false
  if (call.coneTypeOrNull != null) return false
  return call.source == this.source && call.calleeReference.symbol == this.calleeReference.symbol
}

fun Constraint.copy(
  kind: ConstraintKind = this.kind,
  type: KotlinTypeMarker = this.type,
  position: IncorporationConstraintPosition = this.position,
  derivedFrom: Set<TypeVariableMarker> = this.derivedFrom,
  isNullabilityConstraint: Boolean = this.isNullabilityConstraint,
  isNoInfer: Boolean = this.isNoInfer,
  inputTypePositionBeforeIncorporation: OnlyInputTypeConstraintPosition? = this.inputTypePositionBeforeIncorporation,
) = Constraint(
  kind = kind,
  type = type,
  position = position,
  derivedFrom = derivedFrom,
  isNullabilityConstraint = isNullabilityConstraint,
  isNoInfer = isNoInfer,
  inputTypePositionBeforeIncorporation = inputTypePositionBeforeIncorporation,
)