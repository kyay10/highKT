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
import org.jetbrains.kotlin.fir.resolve.toSymbol
import org.jetbrains.kotlin.fir.scopes.FirOverrideChecker
import org.jetbrains.kotlin.fir.scopes.impl.FirStandardOverrideChecker
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.ConeClassLikeLookupTagImpl
import org.jetbrains.kotlin.fir.types.ConeClassLikeLookupTag
import org.jetbrains.kotlin.fir.types.ConeClassLikeType
import org.jetbrains.kotlin.fir.types.ConeInferenceContext
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.ConeRigidType
import org.jetbrains.kotlin.fir.types.ConeTypeApproximator
import org.jetbrains.kotlin.fir.types.ConeTypePreparator
import org.jetbrains.kotlin.fir.types.TypeComponents
import org.jetbrains.kotlin.fir.types.correspondingSupertypesCache
import org.jetbrains.kotlin.fir.types.getConstructor
import org.jetbrains.kotlin.fir.types.replaceType
import org.jetbrains.kotlin.fir.types.resolvedType
import org.jetbrains.kotlin.fir.types.type
import org.jetbrains.kotlin.fir.types.variance
import org.jetbrains.kotlin.fir.types.withArguments
import org.jetbrains.kotlin.fir.types.withAttributes
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.types.AbstractTypeRefiner
import org.jetbrains.kotlin.types.TypeCheckerState
import org.jetbrains.kotlin.types.TypeRefinement
import org.jetbrains.kotlin.types.Variance
import org.jetbrains.kotlin.types.model.AnnotationMarker
import org.jetbrains.kotlin.types.model.KotlinTypeMarker
import org.jetbrains.kotlin.types.model.RigidTypeMarker
import org.jetbrains.kotlin.types.model.TypeArgumentMarker
import org.jetbrains.kotlin.types.model.TypeConstructorMarker

private val UNSAFE = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe").apply { isAccessible = true }
  .get(null) as sun.misc.Unsafe

private val typeContextField = TypeComponents::class.java.getDeclaredField("typeContext").apply {
  isAccessible = true
}

private val typeApproximatorField = TypeComponents::class.java.getDeclaredField("typeApproximator").apply {
  isAccessible = true
}

@OptIn(SessionConfiguration::class)
class KindReturnTypeRefiner(session: FirSession) : FirExpressionResolutionExtension(session), SessionHolder {
  init {
    session.register(TypeComponents::class, TypeComponents(KindInferenceContext(session)))
    session.register(InferenceComponents::class, InferenceComponents(session))
    session.register(FirOverrideChecker::class, FirStandardOverrideChecker(session))
  }

  @OptIn(SymbolInternals::class)
  override fun addNewImplicitReceivers(
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
  (UNSAFE.allocateInstance(TypeComponents::class.java) as TypeComponents).apply {
    typeContextField.set(this, inferenceContext)
    typeApproximatorField.set(this, ConeTypeApproximator(inferenceContext, languageVersionSettings))
  }
}

class ConeClassLikeLookupTagWithType(val underlying: ConeClassLikeLookupTag, val type: ConeRigidType) : ConeClassLikeLookupTag() {
  override val classId: ClassId get() = underlying.classId
  override fun equals(other: Any?) = (other is ConeClassLikeLookupTag) && this.classId == other.classId
  override fun hashCode(): Int = underlying.hashCode()
}

class KindInferenceContext(override val session: FirSession) : ConeInferenceContext, SessionHolder {
  private val underlying = object: ConeInferenceContext { override val session = this@KindInferenceContext.session }
  private fun TypeConstructorMarker.normalize() = if (this is ConeClassLikeLookupTagWithType) this.underlying else this
  override fun TypeConstructorMarker.parametersCount() = with(underlying) { normalize().parametersCount() }
  override fun TypeConstructorMarker.isLocalType() = with(underlying) { normalize().isLocalType() }
  override fun TypeConstructorMarker.toClassLikeSymbol() = with(underlying) { normalize().toClassLikeSymbol() }
  override fun TypeConstructorMarker.supertypes() = with(underlying) { normalize().supertypes() }
  override fun createSimpleType(
    constructor: TypeConstructorMarker,
    arguments: List<TypeArgumentMarker>,
    nullable: Boolean,
    isExtensionFunction: Boolean,
    contextParameterCount: Int,
    attributes: List<AnnotationMarker>?
  ) = with(underlying) {
    createSimpleType(
      constructor.normalize(),
      arguments,
      nullable,
      isExtensionFunction,
      contextParameterCount,
      attributes
    )
  }

  override fun areEqualTypeConstructors(
    c1: TypeConstructorMarker,
    c2: TypeConstructorMarker
  ) = with(underlying) { areEqualTypeConstructors(c1.normalize(), c2.normalize()) }

  override fun RigidTypeMarker.typeConstructor(): TypeConstructorMarker {
    require(this is ConeRigidType)
    return when(val constructor = getConstructor()) {
      is ConeClassLikeLookupTagImpl -> ConeClassLikeLookupTagWithType(constructor, this)
      else -> constructor
    }
  }

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

  private fun ConeKotlinType.cachedCorrespondingSupertypes(constructor: TypeConstructorMarker) =
    session.correspondingSupertypesCache.getCorrespondingSupertypes(this, constructor)

  private fun ConeKotlinType.options(): List<ConeKotlinType> =
    listOfNotNull(attributes.expandedType?.coneType, toCanonicalKType(), this)

  override fun RigidTypeMarker.fastCorrespondingSupertypes(constructor: TypeConstructorMarker): List<ConeClassLikeType>? {
    val superType = (constructor as? ConeClassLikeLookupTagWithType)?.type
    val constructor = constructor.normalize()
    require(this is ConeKotlinType)
    if ((constructor as? ConeClassLikeLookupTag)?.classId != K_CLASS_ID) {
      val superTypes = cachedCorrespondingSupertypes(constructor)
      return if (superType == null) superTypes else superTypes?.map {
        var replacedAny = false
        var i = 0
        it.withArguments { arg ->
          val expectedArg = superType.typeArguments[i++]
          if (expectedArg.type.isK && arg.variance != Variance.OUT_VARIANCE && !arg.type.isK) {
            val replacement = arg.type?.options()?.first()
            if (replacement !== arg.type) {
              replacedAny = true
              arg.replaceType(replacement?.withAttributes(replacement.attributes.add(LeaveUnevaluatedAttribute)))
            } else arg
          } else arg
        }.takeIf { replacedAny } ?: it
      }
    }
    if (toSymbol()?.isTypeFunction() == true) return emptyList()
    return options().flatMap { it.cachedCorrespondingSupertypes(constructor) ?: return null }
  }
}

class KindTypeRefiner(override val session: FirSession) : AbstractTypeRefiner(), SessionHolder {
  @TypeRefinement
  override fun refineType(type: KotlinTypeMarker): KotlinTypeMarker {
    if (type !is ConeKotlinType) return type
    if (type.attributes.shouldLeaveUnevaluated) return type
    return type.applyKOrSelf()
  }
}
