package io.github.kyay10.highkt.fir

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.SessionAndScopeSessionHolder
import org.jetbrains.kotlin.fir.SessionConfiguration
import org.jetbrains.kotlin.fir.SessionHolder
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.extensions.FirExpressionResolutionExtension
import org.jetbrains.kotlin.fir.languageVersionSettings
import org.jetbrains.kotlin.fir.resolve.calls.ImplicitExtensionReceiverValue
import org.jetbrains.kotlin.fir.resolve.inference.InferenceComponents
import org.jetbrains.kotlin.fir.scopes.FirOverrideChecker
import org.jetbrains.kotlin.fir.scopes.impl.FirStandardOverrideChecker
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol
import org.jetbrains.kotlin.fir.types.ConeClassLikeLookupTag
import org.jetbrains.kotlin.fir.types.ConeClassLikeType
import org.jetbrains.kotlin.fir.types.ConeInferenceContext
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.ConeTypeApproximator
import org.jetbrains.kotlin.fir.types.ConeTypePreparator
import org.jetbrains.kotlin.fir.types.TypeComponents
import org.jetbrains.kotlin.fir.types.classId
import org.jetbrains.kotlin.fir.types.correspondingSupertypesCache
import org.jetbrains.kotlin.fir.types.resolvedType
import org.jetbrains.kotlin.types.AbstractTypeRefiner
import org.jetbrains.kotlin.types.TypeCheckerState
import org.jetbrains.kotlin.types.TypeRefinement
import org.jetbrains.kotlin.types.model.CaptureStatus
import org.jetbrains.kotlin.types.model.KotlinTypeMarker
import org.jetbrains.kotlin.types.model.RigidTypeMarker
import org.jetbrains.kotlin.types.model.TypeConstructorMarker
import java.lang.reflect.Field
import java.lang.reflect.Modifier

private val typeContextField = TypeComponents::class.java.getDeclaredField("typeContext").apply {
  isAccessible = true
  val modifiersField: Field = Field::class.java.getDeclaredField("modifiers")
  modifiersField.setAccessible(true)
  modifiersField.setInt(this, modifiers and Modifier.FINAL.inv())
}

private val typeApproximatorField = TypeComponents::class.java.getDeclaredField("typeApproximator").apply {
  isAccessible = true
  val modifiersField: Field = Field::class.java.getDeclaredField("modifiers")
  modifiersField.setAccessible(true)
  modifiersField.setInt(this, modifiers and Modifier.FINAL.inv())
}
@OptIn(SessionConfiguration::class)
class KindReturnTypeRefiner(session: FirSession) : FirExpressionResolutionExtension(session), SessionHolder {
  init {
    session.register(TypeComponents::class, TypeComponents(KindInferenceContext(session)))
    session.register(InferenceComponents::class, InferenceComponents(session))
    session.register(FirOverrideChecker::class, FirStandardOverrideChecker(session))
  }

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
    // TODO figure out how to make resolution see the member scopes of K types so that this is unnecessary
    functionCall.replaceConeTypeOrNull(functionCall.resolvedType.applyKOrSelf())
    return emptyList()
  }
}
private fun TypeComponents(inferenceContext: ConeInferenceContext): TypeComponents = with(inferenceContext.session) {
  TypeComponents(this).apply {
    typeContextField.set(this, inferenceContext)
    typeApproximatorField.set(this, ConeTypeApproximator(inferenceContext, languageVersionSettings))
  }
}

class KindInferenceContext(override val session: FirSession) : ConeInferenceContext, SessionHolder {
  override fun newTypeCheckerState(
    errorTypesEqualToAnything: Boolean,
    stubTypesEqualToAnything: Boolean,
    dnnTypesEqualToFlexible: Boolean,
  ): TypeCheckerState = TypeCheckerState(
    errorTypesEqualToAnything,
    stubTypesEqualToAnything,
    dnnTypesEqualToFlexible,
    allowedTypeVariable = true,
    typeSystemContext = this,
    kotlinTypePreparator = ConeTypePreparator(session),
    kotlinTypeRefiner = KindTypeRefiner(session)
  )

  override fun RigidTypeMarker.fastCorrespondingSupertypes(constructor: TypeConstructorMarker): List<ConeClassLikeType>? {
    require(this is ConeKotlinType)
    if ((constructor as? ConeClassLikeLookupTag)?.classId != K_CLASS_ID || this.classId == K_CLASS_ID)
      return session.correspondingSupertypesCache.getCorrespondingSupertypes(this, constructor)
    return listOfNotNull(toCanonicalKType() as? ConeClassLikeType).map {
      captureFromArguments(it, CaptureStatus.FOR_SUBTYPING) as? ConeClassLikeType ?: it
    }
  }
}

class KindTypeRefiner(override val session: FirSession) : AbstractTypeRefiner(), SessionHolder {
  @TypeRefinement
  override fun refineType(type: KotlinTypeMarker): KotlinTypeMarker {
    if (type !is ConeKotlinType) return type
    return type.applyKOrSelf()
  }
}
