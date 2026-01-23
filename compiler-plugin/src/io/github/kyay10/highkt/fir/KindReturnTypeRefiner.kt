package io.github.kyay10.highkt.fir

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.SessionAndScopeSessionHolder
import org.jetbrains.kotlin.fir.SessionConfiguration
import org.jetbrains.kotlin.fir.SessionHolder
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.extensions.FirExpressionResolutionExtension
import org.jetbrains.kotlin.fir.languageVersionSettings
import org.jetbrains.kotlin.fir.resolve.calls.ImplicitExtensionReceiverValue
import org.jetbrains.kotlin.fir.resolve.directExpansionType
import org.jetbrains.kotlin.fir.resolve.inference.InferenceComponents
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
import org.jetbrains.kotlin.fir.types.abbreviatedType
import org.jetbrains.kotlin.fir.types.abbreviatedTypeOrSelf
import org.jetbrains.kotlin.fir.types.classId
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

class ConeClassLikeLookupTagWithType(val underlying: ConeClassLikeLookupTag, val type: ConeRigidType) :
  ConeClassLikeLookupTag() {
  override val classId: ClassId get() = underlying.classId
  override fun equals(other: Any?) = (other is ConeClassLikeLookupTag) && this.classId == other.classId
  override fun hashCode(): Int = underlying.hashCode()
}

class KindInferenceContext(override val session: FirSession) : ConeInferenceContext, SessionHolder {
  private val underlying = object : ConeInferenceContext {
    override val session = this@KindInferenceContext.session
  }

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
    return when (val constructor = getConstructor()) {
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

  private fun ConeKotlinType.options(): List<ConeKotlinType> = buildList {
    var current: ConeKotlinType? = this@options
    while (current != null) {
      add(current)
      current.toCanonicalKType()?.let(::add)
      current.abbreviatedType?.let {
        (it as? ConeClassLikeType)?.directExpansionType(session)?.abbreviatedType?.toCanonicalKType()?.let(::add)
        it.toCanonicalKType()?.let(::add)
      }
      current = current.attributes.expandedType?.coneType
    }
  }.asReversed()

  override fun RigidTypeMarker.fastCorrespondingSupertypes(constructor: TypeConstructorMarker): List<ConeClassLikeType>? {
    val expectedType = (constructor as? ConeClassLikeLookupTagWithType)?.type
    val constructor = constructor.normalize()
    require(this is ConeKotlinType)
    if (constructor !is ConeClassLikeLookupTag) return null
    if (constructor.classId == CONSTRUCTOR_CLASS_ID && this.classId == CONSTRUCTOR_CLASS_ID) {
      // nominal handling of Constructor types based on abbreviations
      val thisArg = this.typeArguments.firstOrNull()?.type?.abbreviatedTypeOrSelf ?: return null
      val expectedArg = expectedType?.typeArguments?.firstOrNull()?.type?.abbreviatedTypeOrSelf ?: return null
      return if (thisArg.classId != expectedArg.classId) emptyList() else cachedCorrespondingSupertypes(constructor)
    }
    if (constructor.classId != K_CLASS_ID) {
      val superTypes = cachedCorrespondingSupertypes(constructor) ?: return null
      val kIndices = (expectedType ?: return superTypes).typeArguments.mapIndexedNotNull { index, arg ->
        index.takeIf { arg.type.isK }
      }
      return superTypes.flatMap { superType ->
        var acc = listOf(superType)
        val args = superType.typeArguments.ifEmpty { return acc }
        for (index in kIndices) {
          val arg = args[index]
          if (arg.variance == Variance.OUT_VARIANCE) continue
          val argType = arg.type ?: continue
          acc = acc.flatMap { currentType ->
            val options = argType.options().filter { it.isK }
            options.map { option ->
              currentType.withArguments(currentType.typeArguments.toMutableList().apply {
                this[index] = arg.replaceType(
                  option.withAttributes(option.attributes.add(LeaveUnevaluatedAttribute)).takeIf { true })
              }.toTypedArray())
            } + currentType
          }
        }
        acc
      }
    }
    return options().flatMap { it.cachedCorrespondingSupertypes(constructor) ?: return null }
  }
}

class KindTypeRefiner(override val session: FirSession) : AbstractTypeRefiner(), SessionHolder {
  @TypeRefinement
  override fun refineType(type: KotlinTypeMarker) = if (type !is ConeKotlinType) type else type.applyKOrSelf()
}
