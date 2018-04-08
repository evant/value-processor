package me.tatarka.valueprocessor

import javax.annotation.processing.ProcessingEnvironment
import javax.lang.model.element.Modifier
import javax.lang.model.element.TypeElement
import javax.lang.model.util.ElementFilter

/**
 * Represent a value object with some properties. You can obtain one from one of the [ValueCreator] methods.
 */
class Value internal constructor(
    private val env: ProcessingEnvironment,
    /**
     * The target element.
     */
    val element: TypeElement,

    private val creatorSelector: Creator.Selector
) {
    /**
     * The way to create the value.
     */
    @get:Throws(ElementException::class)
    val creator: Creator by lazy(LazyThreadSafetyMode.NONE) {
        creatorSelector.select(element, findCreators())
    }

    /**
     * The properties of the value object.
     */
    @get:Throws(ElementException::class)
    val properties: Properties by lazy(LazyThreadSafetyMode.NONE) {
        Properties(env.typeUtils, creator)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as Value
        if (element != other.element) return false
        return true
    }

    override fun hashCode(): Int {
        return element.hashCode()
    }

    override fun toString(): String {
        return "$element{${properties.joinToString(", ")}}"
    }

    private fun findCreators(): List<Creator> {
        val types = env.typeUtils
        var noArgConstructor: Creator.Constructor? = null
        val result = mutableListOf<Creator>()

        val constructorElements = ElementFilter.constructorsIn(element.enclosedElements)
        if (constructorElements.size == 1 && constructorElements[0].parameters.isEmpty() && !constructorElements[0].modifiers.contains(
                Modifier.PRIVATE
            )
        ) {
            noArgConstructor = Creator.Constructor(element, constructorElements[0])
        } else {
            for (constructor in constructorElements) {
                if (constructor.modifiers.contains(Modifier.PRIVATE)) continue
                result.add(Creator.Constructor(element, constructor))
            }
        }

        for (method in ElementFilter.methodsIn(element.enclosedElements)) {
            if (method.modifiers.contains(Modifier.PRIVATE)
                || !method.modifiers.contains(Modifier.STATIC)
                || !types.isSameType(types.erasure(method.returnType), types.erasure(element.asType()))
            ) continue
            result.add(Creator.Factory(element, method))
        }
        println("$element")

        for (type in ElementFilter.typesIn(element.enclosedElements)) {
            println("$type $element")
            if (type.modifiers.contains(Modifier.PRIVATE) || !type.modifiers.contains(Modifier.STATIC)) continue
            val buildMethod = findBuildMethod(element, type) ?: continue
            var noArgBuilderConstructor: Creator.BuilderConstructor? = null
            val builderConstructorElements = ElementFilter.constructorsIn(type.enclosedElements)
            val builderResults = mutableListOf<Creator>()
            if (builderConstructorElements.size == 1 && builderConstructorElements[0].parameters.isEmpty() && !builderConstructorElements[0].modifiers.contains(
                    Modifier.PRIVATE
                )
            ) {
                noArgBuilderConstructor =
                        Creator.BuilderConstructor(element, type, buildMethod, builderConstructorElements[0])
            } else {
                for (constructor in builderConstructorElements) {
                    if (constructor.modifiers.contains(Modifier.PRIVATE)) continue
                    builderResults.add(Creator.BuilderConstructor(element, type, buildMethod, constructor))
                }
            }
            for (method in ElementFilter.methodsIn(element.enclosedElements)) {
                println("$type $element $method")
                if (method.modifiers.contains(Modifier.PRIVATE)
                    || !method.modifiers.contains(Modifier.STATIC)
                    || !types.isSameType(types.erasure(method.returnType), types.erasure(type.asType()))
                ) continue
                builderResults.add(Creator.BuilderFactory(element, type, buildMethod, method))
            }
            if (builderResults.isEmpty() && noArgBuilderConstructor != null) {
                builderResults.add(noArgBuilderConstructor)
            }
            result.addAll(builderResults)
        }

        if (result.isEmpty() && noArgConstructor != null) {
            result.add(noArgConstructor)
        }

        return result
    }
}

/**
 * Creates instances of [Value] from various starting elements.
 */
class ValueCreator @JvmOverloads constructor(
    private val env: ProcessingEnvironment,
    private val creatorSelector: Creator.Selector = Creator.DefaultSelector()
) {
    /**
     * Creates a [Value] from the given class element.
     */
    @Throws(ElementException::class)
    fun from(element: TypeElement): Value {
        return Value(env, element, creatorSelector)
    }
}