package me.tatarka.valueprocessor

import javax.annotation.processing.ProcessingEnvironment
import javax.lang.model.element.*
import javax.lang.model.util.ElementFilter

/**
 * Represent a value object with some properties. You can obtain one from one of the [ValueCreator] methods.
 */
class Value internal constructor(
    env: ProcessingEnvironment,
    /**
     * Information on how to construct the value object.
     */
    val constructionSource: ConstructionSource
) {
    /**
     * The target element.
     */
    val element: TypeElement = constructionSource.targetClass

    /**
     * The properties of the value object.
     */
    @get:Throws(ElementException::class)
    val properties: Properties by lazy(LazyThreadSafetyMode.NONE) {
        Properties(env.typeUtils, constructionSource)
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
}

/**
 * Creates instances of [Value] from various starting elements.
 */
class ValueCreator(private val env: ProcessingEnvironment) {
    /**
     * Creates a [Value] from the given element. This element can be the [TypeElement] of the target class, or a
     * specific constructor or factory method. If [isBuilder] is true, then the element represents the builder class,
     * constructor or factory method.
     */
    @Throws(ElementException::class)
    fun from(element: Element, isBuilder: Boolean = false): Value = when (element) {
        is TypeElement -> if (isBuilder) fromBuilderClass(element) else fromClass(element)
        is ExecutableElement -> {
            if (element.kind == ElementKind.CONSTRUCTOR) {
                if (isBuilder) fromBuilderConstructor(element) else fromConstructor(element)
            } else {
                if (isBuilder) fromBuilderFactory(element) else fromFactory(element)
            }
        }
        else -> {
            throw IllegalArgumentException("Expected TypeElement or ExecutableElement but got: $element")
        }
    }

    /**
     * Creates a [Value] from the given constructor element. ex:
     * ```
     * public class Value {
     * >   public Value(int arg1) { ... }
     * }
     * ```
     */
    @Throws(ElementException::class)
    fun fromConstructor(constructor: ExecutableElement): Value {
        checkKind(constructor, ElementKind.CONSTRUCTOR)
        return create(ConstructionSource.Constructor(constructor))
    }

    /**
     * Creates a [Value] from the given builder's constructor element. ex:
     * ```
     * public class Builder {
     * >   public Builder() { ... }
     *     public Value build() { ... }
     * }
     * ```
     */
    @Throws(ElementException::class)
    fun fromBuilderConstructor(constructor: ExecutableElement): Value {
        checkKind(constructor, ElementKind.CONSTRUCTOR)
        return create(ConstructionSource.BuilderConstructor(env.typeUtils, constructor))
    }

    /**
     * Creates a [Value] from the given factory method element. ex:
     * ```
     * public class Value {
     * >   public static Value create(int arg) { ... }
     * }
     * ```
     */
    @Throws(ElementException::class)
    fun fromFactory(factory: ExecutableElement): Value {
        checkKind(factory, ElementKind.METHOD)
        return create(ConstructionSource.Factory(env.typeUtils, factory))
    }

    /**
     * Creates a [Value] from the given builder factory method element. ex:
     * ```
     * public class Value {
     * >   public static Builder builder() { ... }
     *     public static class Builder { ... }
     * }
     * ```
     */
    @Throws(ElementException::class)
    fun fromBuilderFactory(builderFactory: ExecutableElement): Value {
        checkKind(builderFactory, ElementKind.METHOD)
        return create(ConstructionSource.BuilderFactory(env.typeUtils, builderFactory))
    }

    /**
     * Creates a [Value] from the given class. ex:
     * ```
     * > public class Value { ... }
     * ```
     */
    @Throws(ElementException::class)
    fun fromClass(targetClass: TypeElement): Value {
        val creator = findConstructorOrFactory(targetClass)
        return if (creator.kind == ElementKind.CONSTRUCTOR) fromConstructor(creator) else fromFactory(creator)
    }

    /**
     * Creates a [Value] from the given builder class. ex:
     * ```
     * > public class Builder {
     *     public Value build() { ... }
     * }
     * ```
     */
    @Throws(ElementException::class)
    fun fromBuilderClass(builderClass: TypeElement): Value {
        val creator = findConstructorOrFactory(builderClass)
        return if (creator.kind == ElementKind.CONSTRUCTOR) fromBuilderConstructor(creator)
        else fromBuilderFactory(creator)
    }

    private fun findConstructorOrFactory(klass: TypeElement): ExecutableElement {
        var noArgConstructor: ExecutableElement? = null
        val constructors = ElementFilter.constructorsIn(klass.enclosedElements)
        if (constructors.size == 1) {
            val constructor = constructors[0]
            if (constructor.parameters.isEmpty()) {
                noArgConstructor = constructor
                constructors.removeAt(0)
            }
        }
        for (method in ElementFilter.methodsIn(klass.enclosedElements)) {
            val modifiers = method.modifiers
            if (modifiers.contains(Modifier.STATIC)
                && !modifiers.contains(Modifier.PRIVATE)
                && method.returnType == klass.asType()
            ) {
                constructors.add(method)
            }
        }
        if (constructors.isEmpty() && noArgConstructor != null) {
            constructors.add(noArgConstructor)
        }
        if (constructors.size == 1) {
            return constructors[0]
        } else {
            val messages =
                mutableListOf(ElementException.Message("More than one constructor or factory method found.", klass))
            constructors.mapTo(messages) { ElementException.Message("  $it", it) }
            throw ElementException(messages)
        }
    }

    private fun create(constructionSource: ConstructionSource): Value = Value(env, constructionSource)

    @Suppress("NOTHING_TO_INLINE")
    private inline fun checkKind(element: Element, kind: ElementKind) {
        if (element.kind != kind) {
            throw IllegalArgumentException("expected $kind but got: $element")
        }
    }
}