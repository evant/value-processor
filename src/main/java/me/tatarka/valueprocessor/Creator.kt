package me.tatarka.valueprocessor

import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.TypeElement

/**
 * A way of creating a value instance. May be a constructor, factory method, or builder.
 */
sealed class Creator(val targetClass: TypeElement) {
    /**
     * The executable element to construct the [Value]. This may be a constructor, factory method, or builder.
     */
    abstract val element: ExecutableElement

    class Constructor internal constructor(targetClass: TypeElement, val constructor: ExecutableElement) :
        Creator(targetClass) {
        override val element = constructor
    }

    class Factory internal constructor(targetClass: TypeElement, val method: ExecutableElement) : Creator(targetClass) {
        override val element = method
    }

    abstract class Builder internal constructor(
        targetClass: TypeElement,
        val builderClass: TypeElement,
        val buildMethod: ExecutableElement
    ) : Creator(targetClass)

    class BuilderConstructor internal constructor(
        targetClass: TypeElement,
        builderClass: TypeElement,
        buildMethod: ExecutableElement,
        val constructor: ExecutableElement
    ) : Builder(targetClass, builderClass, buildMethod) {
        override val element = constructor
    }

    class BuilderFactory internal constructor(
        targetClass: TypeElement,
        builderClass: TypeElement,
        buildMethod: ExecutableElement,
        val method: ExecutableElement
    ) : Builder(targetClass, builderClass, buildMethod) {
        override val element = method
    }

    interface Selector {
        @Throws(ElementException::class)
        fun select(element: TypeElement, creators: List<Creator>): Creator
    }

    open class DefaultSelector : Selector {
        override fun select(element: TypeElement, creators: List<Creator>): Creator =
            when (creators.size) {
                0 -> throw ElementException("No creation methods found on $element", element)
                1 -> creators.first()
                else -> {
                    val messages = mutableListOf(
                        ElementException.Message(
                            "More than one creation methods found on $element",
                            element
                        )
                    )
                    creators.mapTo(messages) { ElementException.Message("  ${it.element}", it.element) }
                    throw ElementException(messages)
                }
            }
    }

    class AnnotationSelector(private val annotationClass: Class<out Annotation>) : DefaultSelector() {
        override fun select(element: TypeElement, creators: List<Creator>): Creator {
            val filteredCreators = creators.filter { it.element.getAnnotation(annotationClass) != null }
            return when (filteredCreators.size) {
                1 -> filteredCreators.first()
                else -> super.select(element, creators)
            }
        }
    }
}