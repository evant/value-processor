package me.tatarka.valueprocessor

import java.util.*
import javax.lang.model.element.ElementKind
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.TypeElement
import javax.lang.model.type.TypeKind
import javax.lang.model.util.ElementFilter
import javax.lang.model.util.Types

/**
 * How a [Value] can be constructed. Either a constructor, factory method, or builder.
 */
sealed class ConstructionSource {
    /**
     * The target [Value] class to construct.
     */
    abstract val targetClass: TypeElement
    /**
     * The executable element to construct the [Value]. This may be a constructor, factory method, or builder.
     */
    abstract val constructionElement: ExecutableElement
    /**
     * If this source is a constructor (either of the value or the builder).
     */
    abstract val isConstructor: Boolean
    /**
     * If this source is a builder.
     */
    abstract val isBuilder: Boolean

    /**
     * A [Value] constructor.
     */
    class Constructor internal constructor(
        /**
         * A [Value] constructor element.
         */
        val constructor: ExecutableElement
    ) : ConstructionSource() {
        override val targetClass: TypeElement by lazy(LazyThreadSafetyMode.NONE) {
            constructor.enclosingElement as TypeElement
        }

        override val constructionElement = constructor

        override val isConstructor: Boolean = true

        override val isBuilder: Boolean = false
    }

    /**
     * A [Value] factory method.
     */
    class Factory internal constructor(
        private val types: Types,
        /**
         * A [Value] factory method element.
         */
        val method: ExecutableElement
    ) : ConstructionSource() {
        override val targetClass: TypeElement by lazy(LazyThreadSafetyMode.NONE) {
            types.asElement(method.returnType) as TypeElement
        }

        override val constructionElement = method

        override val isConstructor: Boolean = false

        override val isBuilder: Boolean = false
    }

    abstract class Builder : ConstructionSource() {
        abstract val builderClass: TypeElement
        abstract val buildMethod: ExecutableElement
        override val isBuilder: Boolean = true
    }

    class BuilderConstructor internal constructor(val types: Types, val constructor: ExecutableElement) : Builder() {
        override val builderClass: TypeElement by lazy(LazyThreadSafetyMode.NONE) {
            constructor.enclosingElement as TypeElement
        }

        override val buildMethod: ExecutableElement by lazy(LazyThreadSafetyMode.NONE) {
            findBuildMethod(constructor.enclosingElement as TypeElement)
                    ?: throw ElementException("Can't find build method on builder", constructor.enclosingElement)
        }

        override val targetClass: TypeElement by lazy(LazyThreadSafetyMode.NONE) {
            types.asElement(buildMethod.returnType) as TypeElement
        }

        override val constructionElement = constructor

        override val isConstructor: Boolean = true
    }

    class BuilderFactory internal constructor(val types: Types, val method: ExecutableElement) : Builder() {
        override val builderClass: TypeElement by lazy(LazyThreadSafetyMode.NONE) {
            types.asElement(method.returnType) as TypeElement
        }

        override val buildMethod: ExecutableElement by lazy(LazyThreadSafetyMode.NONE) {
            findBuildMethod(types.asElement(method.returnType) as TypeElement)!!
        }

        override val targetClass: TypeElement by lazy(LazyThreadSafetyMode.NONE) {
            types.asElement(buildMethod.returnType) as TypeElement
        }

        override val constructionElement = method

        override val isConstructor: Boolean = false
    }

    protected fun findBuildMethod(builderClass: TypeElement): ExecutableElement? {
        // Ok, maybe there is just one possible builder method.
        run {
            var candidate: ExecutableElement? = null
            var foundMultipleCandidates = false
            var isCandidateReasonableBuilderMethodName = false
            for (method in ElementFilter.methodsIn(builderClass.enclosedElements)) {
                if (isPossibleBuilderMethod(method, builderClass)) {
                    if (candidate == null) {
                        candidate = method
                    } else {
                        // Multiple possible methods, keep the one with a reasonable builder name if
                        // possible.
                        foundMultipleCandidates = true
                        isCandidateReasonableBuilderMethodName = isCandidateReasonableBuilderMethodName ||
                                isReasonableBuilderMethodName(
                                    candidate
                                )
                        if (isCandidateReasonableBuilderMethodName) {
                            if (isReasonableBuilderMethodName(method)) {
                                // both reasonable, too ambiguous.
                                candidate = null
                                break
                            }
                        } else {
                            candidate = method
                        }
                    }
                }
            }
            if (candidate != null && (!foundMultipleCandidates || isCandidateReasonableBuilderMethodName)) {
                return candidate
            }
        }
        // Last try, check to see if the immediate parent class makes sense.
        run {
            val candidate = builderClass.enclosingElement
            if (candidate.kind == ElementKind.CLASS) {
                for (method in ElementFilter.methodsIn(builderClass.enclosedElements)) {
                    if (method.returnType == candidate.asType() && method.parameters.isEmpty()) {
                        return method
                    }
                }
            }
        }
        // Well, I give up.
        return null
    }

    /**
     * A possible builder method has no parameters and a return type of the class we want to
     * construct. Therefore, the return type is not going to be void, primitive, or a platform
     * class.
     */
    private fun isPossibleBuilderMethod(method: ExecutableElement, builderClass: TypeElement): Boolean {
        if (!method.parameters.isEmpty()) {
            return false
        }
        val returnType = method.returnType
        if (returnType.kind == TypeKind.VOID) {
            return false
        }
        if (returnType.kind.isPrimitive) {
            return false
        }
        if (returnType == builderClass.asType()) {
            return false
        }
        val returnTypeName = returnType.toString()
        return !(returnTypeName.startsWith("java.")
                || returnTypeName.startsWith("javax.")
                || returnTypeName.startsWith("android."))
    }

    private fun isReasonableBuilderMethodName(method: ExecutableElement): Boolean {
        val methodName = method.simpleName.toString().toLowerCase(Locale.US)
        return methodName.startsWith("build") || methodName.startsWith("create")
    }
}
