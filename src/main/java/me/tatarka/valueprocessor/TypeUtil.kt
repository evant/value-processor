package me.tatarka.valueprocessor

import java.util.*
import javax.lang.model.element.ElementKind
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.TypeElement
import javax.lang.model.type.TypeMirror
import javax.lang.model.util.ElementFilter

internal fun isPlatformType(type: TypeMirror): Boolean {
    val typeName = type.toString()
    return typeName.startsWith("java.")
            || typeName.startsWith("javax.")
            || typeName.startsWith("android.")
}

internal fun findBuildMethod(targetClass: TypeElement, builderClass: TypeElement): ExecutableElement? {
    // Ok, maybe there is just one possible builder method.
    run {
        var candidate: ExecutableElement? = null
        var foundMultipleCandidates = false
        var isCandidateReasonableBuilderMethodName = false
        for (method in ElementFilter.methodsIn(builderClass.enclosedElements)) {
            if (isPossibleBuilderMethod(method, targetClass)) {
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
internal fun isPossibleBuilderMethod(method: ExecutableElement, targetClass: TypeElement): Boolean =
    method.parameters.isEmpty() && method.returnType == targetClass.asType()

internal fun isReasonableBuilderMethodName(method: ExecutableElement): Boolean {
    val methodName = method.simpleName.toString().toLowerCase(Locale.US)
    return methodName.startsWith("build") || methodName.startsWith("create")
}
