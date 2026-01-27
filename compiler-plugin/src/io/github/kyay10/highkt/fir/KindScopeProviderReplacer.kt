package io.github.kyay10.highkt.fir

import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.FirImplementationDetail
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.SessionHolder
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.expression.ExpressionCheckers
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirQualifiedAccessExpressionChecker
import org.jetbrains.kotlin.fir.analysis.extensions.FirAdditionalCheckersExtension
import org.jetbrains.kotlin.fir.declarations.FirClass
import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.declarations.FirDeclarationOrigin
import org.jetbrains.kotlin.fir.declarations.FirResolvePhase
import org.jetbrains.kotlin.fir.declarations.builder.buildRegularClassCopy
import org.jetbrains.kotlin.fir.declarations.utils.classId
import org.jetbrains.kotlin.fir.expressions.FirQualifiedAccessExpression
import org.jetbrains.kotlin.fir.extensions.FirStatusTransformerExtension
import org.jetbrains.kotlin.fir.resolve.ScopeSession
import org.jetbrains.kotlin.fir.resolve.ScopeSessionKey
import org.jetbrains.kotlin.fir.resolve.calls.fullyExpandedClass
import org.jetbrains.kotlin.fir.resolve.createSubstitutionForScope
import org.jetbrains.kotlin.fir.resolve.defaultType
import org.jetbrains.kotlin.fir.resolve.fullyExpandedType
import org.jetbrains.kotlin.fir.resolve.substitution.ConeRawScopeSubstitutor
import org.jetbrains.kotlin.fir.resolve.substitution.ConeSubstitutor
import org.jetbrains.kotlin.fir.resolve.substitution.substitutorByMap
import org.jetbrains.kotlin.fir.resolve.toClassSymbol
import org.jetbrains.kotlin.fir.resolve.toSymbol
import org.jetbrains.kotlin.fir.scopes.ConeSubstitutionScopeKey
import org.jetbrains.kotlin.fir.scopes.FirKotlinScopeProvider
import org.jetbrains.kotlin.fir.scopes.FirTypeScope
import org.jetbrains.kotlin.fir.scopes.impl.FirClassSubstitutionScope
import org.jetbrains.kotlin.fir.scopes.kotlinScopeProvider
import org.jetbrains.kotlin.fir.scopes.unsubstitutedScope
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.types.ConeClassLikeLookupTag
import org.jetbrains.kotlin.fir.types.ConeClassLikeType
import org.jetbrains.kotlin.fir.types.lowerBoundIfFlexible
import org.jetbrains.kotlin.fir.types.resolvedType
import org.jetbrains.kotlin.util.PrivateForInline

class ReplaceKDispatchReceiversExtension(session: FirSession) : FirAdditionalCheckersExtension(session) {
  override val expressionCheckers: ExpressionCheckers
    get() =
      object : ExpressionCheckers() {
        override val qualifiedAccessExpressionCheckers: Set<FirQualifiedAccessExpressionChecker>
          get() = setOf(Checker())
      }

  class Checker : FirQualifiedAccessExpressionChecker(MppCheckerKind.Common) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(expression: FirQualifiedAccessExpression) {
      val receiver = expression.dispatchReceiver ?: return
      val type = receiver.resolvedType
      val applied = type.applyKOrSelf()
      if (applied !== type) receiver.replaceConeTypeOrNull(applied) // TODO might throw error
    }
  }
}

@OptIn(FirImplementationDetail::class, SymbolInternals::class, PrivateForInline::class)
class KindScopeProviderReplacer(session: FirSession) : FirStatusTransformerExtension(session), SessionHolder {
  private val scopesField = ScopeSession::class.java.getDeclaredField("scopes").apply { isAccessible = true }
  var initialized = false

  override fun needTransformStatus(declaration: FirDeclaration): Boolean {
    if (!initialized) {
      initialized = true
      val symbol = K_CLASS_ID.toSymbol() as? FirRegularClassSymbol ?: return false
      val originalDecorator = session.kotlinScopeProvider.declaredMemberScopeDecorator
      symbol.bind(
        buildRegularClassCopy(symbol.fir) {
          scopeProvider =
            FirKotlinScopeProvider { klass, declaredMemberScope, useSiteSession, scopeSession, memberRequiredPhase ->
              try {
                scopesField.set(
                  scopeSession,
                  ScopeSessionMap(useSiteSession, scopeSession).apply { putAll(scopeSession.scopes()) },
                )
              } catch (_: Exception) {}
              originalDecorator(klass, declaredMemberScope, useSiteSession, scopeSession, memberRequiredPhase)
            }
          this.symbol = symbol
        }
      )
    }
    return false
  }
}

class ScopeSessionMap(val session: FirSession, val scopeSession: ScopeSession) :
  HashMap<Any, HashMap<ScopeSessionKey<*, *>, Any>>() {
  override fun containsKey(key: Any) =
    super.containsKey(key) || (key is FirClass && key.fullyExpandedClass(session)?.classId == K_CLASS_ID)

  override fun get(key: Any) =
    when {
      super.containsKey(key) -> super.get(key)
      key is FirClass && key.fullyExpandedClass(session)?.classId == K_CLASS_ID ->
        ScopeSessionKeyMap(key, session, scopeSession).also { put(key, it) }
      else -> null
    }
}

class ScopeSessionKeyMap(
  private val kClass: FirClass,
  override val session: FirSession,
  val scopeSession: ScopeSession,
) : HashMap<ScopeSessionKey<*, *>, Any>(), SessionHolder {
  override fun get(key: ScopeSessionKey<*, *>): Any? =
    when {
      super.containsKey(key) -> super.get(key)
      key is ConeSubstitutionScopeKey -> {
        val substitutor = key.substitutor
        val kType = substitutor.substituteOrSelf(kClass.defaultType())
        val isRaw = substitutor is ConeRawScopeSubstitutor
        val applied = kType.applyKOrSelf()
        if (applied !is ConeClassLikeType || applied === kType) return super.get(key)
        (applied.classScope(session, scopeSession, null, isRaw) ?: return super.get(key)).also { put(key, it) }
      }
      else -> super.get(key)
    }
}

@OptIn(SymbolInternals::class)
private fun ConeClassLikeType.classScope(
  useSiteSession: FirSession,
  scopeSession: ScopeSession,
  requiredMembersPhase: FirResolvePhase?,
  isRaw: Boolean,
): FirTypeScope? {
  val fullyExpandedType = fullyExpandedType(useSiteSession)
  val fir = fullyExpandedType.lookupTag.toClassSymbol(useSiteSession)?.fir ?: return null
  val substitutor =
    when {
      isRaw -> ConeRawScopeSubstitutor(useSiteSession)
      else ->
        substitutorByMap(
          createSubstitutionForScope(fir.typeParameters, fullyExpandedType, useSiteSession),
          useSiteSession,
        )
    }

  return fir.scopeForClass(substitutor, useSiteSession, scopeSession, lookupTag, requiredMembersPhase)
}

fun FirClass.scopeForClass(
  substitutor: ConeSubstitutor,
  useSiteSession: FirSession,
  scopeSession: ScopeSession,
  memberOwnerLookupTag: ConeClassLikeLookupTag,
  memberRequiredPhase: FirResolvePhase?,
): FirTypeScope =
  scopeForClassImpl(
    substitutor,
    useSiteSession,
    scopeSession,
    skipPrivateMembers = false,
    classFirDispatchReceiver = this,
    isFromExpectClass = false,
    memberOwnerLookupTag = memberOwnerLookupTag,
    memberRequiredPhase = memberRequiredPhase,
  )

private fun FirClass.scopeForClassImpl(
  substitutor: ConeSubstitutor,
  useSiteSession: FirSession,
  scopeSession: ScopeSession,
  skipPrivateMembers: Boolean,
  classFirDispatchReceiver: FirClass,
  isFromExpectClass: Boolean,
  memberOwnerLookupTag: ConeClassLikeLookupTag?,
  memberRequiredPhase: FirResolvePhase?,
): FirTypeScope {
  val basicScope =
    unsubstitutedScope(useSiteSession, scopeSession, withForcedTypeCalculator = false, memberRequiredPhase)
  if (substitutor == ConeSubstitutor.Empty) return basicScope

  val key =
    ConeSubstitutionScopeKey(
      classFirDispatchReceiver.symbol.toLookupTag(),
      isFromExpectClass,
      substitutor,
      memberOwnerLookupTag,
    )
  val type =
    substitutor.substituteOrSelf(classFirDispatchReceiver.defaultType()).lowerBoundIfFlexible() as ConeClassLikeType

  return FirClassSubstitutionScope(
    useSiteSession,
    basicScope,
    key,
    substitutor,
    type,
    skipPrivateMembers,
    makeExpect = isFromExpectClass,
    memberOwnerLookupTag ?: classFirDispatchReceiver.symbol.toLookupTag(),
    origin =
      if (classFirDispatchReceiver != this) {
        FirDeclarationOrigin.SubstitutionOverride.DeclarationSite
      } else {
        FirDeclarationOrigin.SubstitutionOverride.CallSite
      },
  )
}
