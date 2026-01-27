package io.github.kyay10.highkt.fir

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.SessionAndScopeSessionHolder
import org.jetbrains.kotlin.fir.SessionConfiguration
import org.jetbrains.kotlin.fir.SessionHolder
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.extensions.FirExpressionResolutionExtension
import org.jetbrains.kotlin.fir.languageVersionSettings
import org.jetbrains.kotlin.fir.resolve.calls.ImplicitExtensionReceiverValue
import org.jetbrains.kotlin.fir.resolve.fullyExpandedType
import org.jetbrains.kotlin.fir.resolve.inference.InferenceComponents
import org.jetbrains.kotlin.fir.scopes.FirOverrideChecker
import org.jetbrains.kotlin.fir.scopes.impl.FirStandardOverrideChecker
import org.jetbrains.kotlin.fir.symbols.ConeTypeParameterLookupTag
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.ConeClassLikeLookupTagImpl
import org.jetbrains.kotlin.fir.symbols.impl.FirTypeParameterSymbol
import org.jetbrains.kotlin.fir.types.ConeCapturedType
import org.jetbrains.kotlin.fir.types.ConeClassLikeLookupTag
import org.jetbrains.kotlin.fir.types.ConeClassLikeType
import org.jetbrains.kotlin.fir.types.ConeInferenceContext
import org.jetbrains.kotlin.fir.types.ConeIntegerConstantOperatorType
import org.jetbrains.kotlin.fir.types.ConeIntegerLiteralConstantType
import org.jetbrains.kotlin.fir.types.ConeIntersectionType
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.ConeLookupTagBasedType
import org.jetbrains.kotlin.fir.types.ConeRigidType
import org.jetbrains.kotlin.fir.types.ConeStubTypeForTypeVariableInSubtyping
import org.jetbrains.kotlin.fir.types.ConeTypeApproximator
import org.jetbrains.kotlin.fir.types.ConeTypeParameterType
import org.jetbrains.kotlin.fir.types.ConeTypePreparator
import org.jetbrains.kotlin.fir.types.ConeTypeVariableType
import org.jetbrains.kotlin.fir.types.TypeComponents
import org.jetbrains.kotlin.fir.types.abbreviatedType
import org.jetbrains.kotlin.fir.types.abbreviatedTypeOrSelf
import org.jetbrains.kotlin.fir.types.classId
import org.jetbrains.kotlin.fir.types.correspondingSupertypesCache
import org.jetbrains.kotlin.fir.types.getConstructor
import org.jetbrains.kotlin.fir.types.isNullableAny
import org.jetbrains.kotlin.fir.types.replaceType
import org.jetbrains.kotlin.fir.types.resolvedType
import org.jetbrains.kotlin.fir.types.type
import org.jetbrains.kotlin.fir.types.unwrapToSimpleTypeUsingLowerBound
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

private val UNSAFE =
  Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null)
    as sun.misc.Unsafe

private val typeContextField = TypeComponents::class.java.getDeclaredField("typeContext").apply { isAccessible = true }

private val typeApproximatorField =
  TypeComponents::class.java.getDeclaredField("typeApproximator").apply { isAccessible = true }

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
    containingCallableSymbol: FirBasedSymbol<*>,
  ): List<ImplicitExtensionReceiverValue> {
    // This exists as a fallback in case scope replacement ever breaks
    functionCall.replaceConeTypeOrNull(functionCall.resolvedType.applyKOrSelf())
    return emptyList()
  }
}

private fun TypeComponents(inferenceContext: ConeInferenceContext): TypeComponents =
  with(inferenceContext.session) {
    (UNSAFE.allocateInstance(TypeComponents::class.java) as TypeComponents).apply {
      typeContextField.set(this, inferenceContext)
      typeApproximatorField.set(this, ConeTypeApproximator(inferenceContext, languageVersionSettings))
    }
  }

class ConeClassLikeLookupTagWithType(val underlying: ConeClassLikeLookupTag, val type: ConeRigidType) :
  ConeClassLikeLookupTag() {
  override val classId: ClassId
    get() = underlying.classId

  override fun equals(other: Any?) = (other is ConeClassLikeLookupTag) && this.classId == other.classId

  override fun hashCode(): Int = underlying.hashCode()
}

class KindInferenceContext(override val session: FirSession) : ConeInferenceContext, SessionHolder {
  private val underlying =
    object : ConeInferenceContext {
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
    attributes: List<AnnotationMarker>?,
  ) =
    with(underlying) {
      createSimpleType(
        constructor.normalize(),
        arguments,
        nullable,
        isExtensionFunction,
        contextParameterCount,
        attributes,
      )
    }

  override fun areEqualTypeConstructors(c1: TypeConstructorMarker, c2: TypeConstructorMarker) =
    with(underlying) { areEqualTypeConstructors(c1.normalize(), c2.normalize()) }

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
  ): TypeCheckerState =
    TypeCheckerState(
      errorTypesEqualToAnything,
      stubTypesEqualToAnything,
      dnnTypesEqualToFlexible,
      allowedTypeVariable = true,
      typeSystemContext = this,
      kotlinTypePreparator = ConeTypePreparator(session),
      kotlinTypeRefiner = KindTypeRefiner(session),
    )

  private fun ConeKotlinType.cachedCorrespondingSupertypes(
    constructor: TypeConstructorMarker,
    expectedType: ConeRigidType?,
  ): List<ConeClassLikeType>? {
    val superTypes = session.correspondingSupertypesCache.getCorrespondingSupertypes(this, constructor)
    return if (expectedType == null) superTypes
    else
      superTypes?.map { superType ->
        var replacedAny = false
        val newArgs =
          superType.typeArguments.mapIndexed { i, arg ->
            if (expectedType.typeArguments[i].type?.hasNonTrivialBounds() == true) {
              if (arg.variance == Variance.OUT_VARIANCE) return@mapIndexed arg
              // deconstructing/pattern-matching, thus apply arguments
              val applied = arg.type?.applyKOrSelf()?.takeIf { it !== arg.type } ?: return@mapIndexed arg
              replacedAny = true
              arg.replaceType(applied.takeIf { true })
            } else arg
          }
        if (replacedAny) superType.withArgumentsSafe(newArgs.toTypedArray()) else superType
      }
  }

  private fun ConeKotlinType.options(): List<ConeKotlinType> = buildList {
    // Invariant: current.isK
    // If we don't have an expanded type, we try to make one from the abbreviation
    // else, we use the canonical form of this type
    var current: ConeKotlinType? =
      (abbreviatedType as? ConeClassLikeType)
        ?.takeIf { expandedType == null && !it.isK }
        ?.let { it.fullyExpandedTypeWithAttribute().withExpanded(it.toCanonicalKType()).toCanonicalKType() }
        ?: toCanonicalKType()
    while (current != null) {
      add(current)
      current = current.expandedType
    }
  }

  override fun RigidTypeMarker.fastCorrespondingSupertypes(
    constructor: TypeConstructorMarker
  ): List<ConeClassLikeType>? {
    val expectedType = (constructor as? ConeClassLikeLookupTagWithType)?.type
    val constructor = constructor.normalize()
    require(this is ConeKotlinType)
    return when {
      constructor !is ConeClassLikeLookupTag -> null
      constructor.classId == CONSTRUCTOR_CLASS_ID && this.classId == CONSTRUCTOR_CLASS_ID -> {
        // nominal handling of Constructor types based on abbreviations
        val thisArg = this.typeArguments.firstOrNull()?.type?.abbreviatedTypeOrSelf ?: return null
        val expectedArg = expectedType?.typeArguments?.firstOrNull()?.type?.abbreviatedTypeOrSelf ?: return null
        if (thisArg.classId != expectedArg.classId) emptyList() else cachedCorrespondingSupertypes(constructor, null)
      }

      constructor.classId == K_CLASS_ID ->
        options().flatMap { it.cachedCorrespondingSupertypes(constructor, null) ?: return null }

      else -> {
        val superTypes = cachedCorrespondingSupertypes(constructor, expectedType) ?: return null
        val kIndices =
          (expectedType ?: return superTypes).typeArguments.mapIndexedNotNull { index, arg ->
            index.takeIf { arg.type.isK }
          }
        superTypes.flatMap { superType ->
          var acc = listOf(superType)
          val args =
            superType.typeArguments.ifEmpty {
              return@flatMap acc
            }
          for (index in kIndices) {
            val arg = args[index]
            if (arg.variance == Variance.OUT_VARIANCE) continue
            val argType = arg.type ?: continue
            acc =
              acc.flatMap { currentType ->
                argType.options().map { option ->
                  currentType.withArguments(
                    currentType.typeArguments
                      .toMutableList()
                      .apply {
                        this[index] =
                          arg.replaceType(
                            option.withAttributes(option.attributes.add(LeaveUnevaluatedAttribute)).takeIf { true }
                          )
                      }
                      .toTypedArray()
                  )
                } + currentType
              }
          }
          acc
        }
      }
    }
  }
}

class KindTypeRefiner(override val session: FirSession) : AbstractTypeRefiner(), SessionHolder {
  @TypeRefinement
  override fun refineType(type: KotlinTypeMarker) = if (type !is ConeKotlinType) type else type.applyKOrSelf()
}

context(_: SessionHolder)
private fun ConeKotlinType.hasNonTrivialBounds(): Boolean =
  when (val type = unwrapToSimpleTypeUsingLowerBound()) {
    is ConeClassLikeType -> !fullyExpandedType().isNullableAny
    is ConeTypeParameterType -> type.lookupTag.typeParameterSymbol.hasNonTrivialBounds()
    is ConeTypeVariableType ->
      (type.typeConstructor.originalTypeParameter as? ConeTypeParameterLookupTag)?.symbol?.hasNonTrivialBounds() == true
    is ConeStubTypeForTypeVariableInSubtyping ->
      (type.constructor.variable.typeConstructor.originalTypeParameter as? ConeTypeParameterLookupTag)
        ?.symbol
        ?.hasNonTrivialBounds() == true
    is ConeIntersectionType -> type.intersectedTypes.any { it.hasNonTrivialBounds() }
    is ConeIntegerConstantOperatorType,
    is ConeIntegerLiteralConstantType -> true

    is ConeCapturedType,
    is ConeLookupTagBasedType -> false
  }

context(_: SessionHolder)
private fun FirTypeParameterSymbol.hasNonTrivialBounds(): Boolean =
  resolvedBounds.any { it.coneType.hasNonTrivialBounds() }
