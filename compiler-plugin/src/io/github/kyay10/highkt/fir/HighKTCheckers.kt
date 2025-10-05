package io.github.kyay10.highkt.fir

import io.github.kyay10.highkt.fir.HighKTErrors.EXPAND_TO_MISMATCH
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactory3
import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactoryToRendererMap
import org.jetbrains.kotlin.diagnostics.KtDiagnosticsContainer
import org.jetbrains.kotlin.diagnostics.Severity.ERROR
import org.jetbrains.kotlin.diagnostics.SourceElementPositioningStrategies
import org.jetbrains.kotlin.diagnostics.rendering.BaseDiagnosticRendererFactory
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.SessionHolder
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.expression.ExpressionCheckers
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirFunctionCallChecker
import org.jetbrains.kotlin.fir.analysis.diagnostics.FirDiagnosticRenderers.RENDER_TYPE
import org.jetbrains.kotlin.fir.analysis.extensions.FirAdditionalCheckersExtension
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.references.resolved
import org.jetbrains.kotlin.fir.references.toResolvedCallableSymbol
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.isSubtypeOf
import org.jetbrains.kotlin.fir.types.resolvedType
import org.jetbrains.kotlin.psi.KtCallExpression

class HighKTCheckers(session: FirSession) : FirAdditionalCheckersExtension(session), SessionHolder {
  override val expressionCheckers: ExpressionCheckers
    get() = object : ExpressionCheckers() {
      override val functionCallCheckers = setOf(ExpandToCallChecker)
    }
}

private object ExpandToCallChecker : FirFunctionCallChecker(MppCheckerKind.Common) {
  context(context: CheckerContext, reporter: DiagnosticReporter)
  override fun check(expression: FirFunctionCall) {
    if (expression.calleeReference.resolved?.toResolvedCallableSymbol()?.callableId != EXPAND_TO_ID) return
    val receiver = expression.explicitReceiver ?: return
    val newType = expression.resolvedType.applyKEverywhere()
    if (newType == null || !receiver.resolvedType.isSubtypeOf(newType, context.session)) {
      reporter.reportOn(expression.source, EXPAND_TO_MISMATCH, receiver.resolvedType, expression.resolvedType, newType ?: expression.resolvedType)
    }
  }
}

private object HighKTErrors : KtDiagnosticsContainer() {
  val EXPAND_TO_MISMATCH: KtDiagnosticFactory3<ConeKotlinType, ConeKotlinType, ConeKotlinType> = KtDiagnosticFactory3(
    "EXPAND_TO_MISMATCH", ERROR, SourceElementPositioningStrategies.DEFAULT,
    KtCallExpression::class,
    getRendererFactory()
  )

  override fun getRendererFactory(): BaseDiagnosticRendererFactory = HighKTErrorsDefaultMessages
}

private object HighKTErrorsDefaultMessages : BaseDiagnosticRendererFactory() {
  override val MAP: KtDiagnosticFactoryToRendererMap by KtDiagnosticFactoryToRendererMap("FIR") { map ->
    map.put(EXPAND_TO_MISMATCH, "Type {0} does not expand to {1}, since the latter contracts to {2}", RENDER_TYPE, RENDER_TYPE, RENDER_TYPE)
  }
}