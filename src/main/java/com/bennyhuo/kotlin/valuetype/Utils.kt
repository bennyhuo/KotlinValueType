package com.bennyhuo.kotlin.valuetype

import org.jetbrains.kotlin.descriptors.annotations.AnnotationDescriptor
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.debugger.sequence.psi.resolveType
import org.jetbrains.kotlin.idea.inspections.AbstractPrimitiveRangeToInspection.Companion.constantValueOrNull
import org.jetbrains.kotlin.idea.refactoring.fqName.fqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.lastBlockStatementOrThis
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.constants.ArrayValue
import org.jetbrains.kotlin.resolve.constants.ConstantValue
import org.jetbrains.kotlin.resolve.descriptorUtil.annotationClass
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.types.KotlinType

/**
 * Created by benny.
 */
/**
 * Computes the qualified name of this [KtAnnotationEntry].
 * Prefer to use [fqNameMatches], which checks the short name first and thus has better performance.
 */
fun KtAnnotationEntry.getQualifiedName(): String? {
    return analyze(BodyResolveMode.PARTIAL).get(BindingContext.ANNOTATION, this)?.fqName?.asString()
}

/**
 * Determines whether this [KtAnnotationEntry] has the specified qualified name.
 * Careful: this does *not* currently take into account Kotlin type aliases (https://kotlinlang.org/docs/reference/type-aliases.html).
 *   Fortunately, type aliases are extremely uncommon for simple annotation types.
 */
fun KtAnnotationEntry.fqNameMatches(fqName: String): Boolean {
    // For inspiration, see IDELightClassGenerationSupport.KtUltraLightSupportImpl.findAnnotation in the Kotlin plugin.
    val shortName = shortName?.asString() ?: return false
    return fqName.endsWith(shortName) && fqName == getQualifiedName()
}

fun KtNameReferenceExpression.definedConstantValueOrNull(): ConstantValue<*>? {
    return resolveType().definedConstantValueOrNull()
}

fun KotlinType?.definedConstantValueOrNull(): ConstantValue<*>? {
    return this?.annotations?.firstNotNullOfOrNull {
        it.annotationClass?.annotations
            ?.findAnnotation(VALUE_TYPE_FQNAME)
            ?.allValueArguments?.get(Name.identifier("value"))
    }
}

val KotlinType.valueTypeAnnotation: AnnotationDescriptor?
    get() = this.annotations.firstNotNullOfOrNull {
        it.annotationClass?.annotations
            ?.findAnnotation(VALUE_TYPE_FQNAME)
    }

fun KotlinType.isCompatibleWith(otherType: KotlinType): Boolean {
    val thisValueTypeAnnotation = this.valueTypeAnnotation ?: return false
    val otherValueTypeAnnotation = otherType.valueTypeAnnotation ?: return false
    return thisValueTypeAnnotation.fqName == otherType.fqName
}

fun KtExpression.possibleConstantValuesOrNull(): Any? {
    return constantValueOrNull()?.value ?: resolveType().definedConstantValueOrNull()
}

fun KtExpression?.isUnsafeValueType(): Boolean {
    return this != null
            && this is KtAnnotatedExpression
            && annotationEntries.any { it.fqNameMatches(UNSAFE_VALUE_TYPE_NAME) }
}

fun KtNamedFunction.isReturningUnsafeValueType(): Boolean {
    return returningExpression?.isUnsafeValueType() ?: false
}

val KtNamedFunction.returningExpression: KtExpression?
    get() {
        return if (hasBlockBody()) {
            bodyBlockExpression?.lastBlockStatementOrThis()
                ?.safeAs<KtReturnExpression>()?.returnedExpression
        } else if (hasInitializer()) {
            initializer
        } else {
            null
        }
    }

fun KtNamedFunction.possibleReturnValuesOrNull(): Any? {
    return returningExpression?.possibleConstantValuesOrNull()
}

inline fun <reified T> Any?.safeAs() = this as? T
inline fun <reified T> Any.caseAs() = this as T