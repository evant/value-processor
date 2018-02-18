package me.tatarka.valueprocessor

import javax.lang.model.element.AnnotationMirror
import javax.lang.model.element.Element
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.VariableElement
import javax.lang.model.type.TypeKind
import javax.lang.model.type.TypeMirror

/**
 * A value object's property. This can either be a public field, getter, or a constructor param.
 */
sealed class Property<out E : Element>(val element: E) {
    /**
     * The name of the property. For fields and params this is the name in code. For getters, it may have the 'get' or
     * 'is' prefix stripped.
     * @see callableName
     */
    open val name: String
        get() = element.simpleName.toString()

    /**
     * The actual name of the property. This will not have any 'get' or 'is' prefix stripped.
     * @see name
     */
    open val callableName: String
        get() = name

    /**
     * The property's type.
     */
    open val type: TypeMirror
        get() = element.asType()

    /**
     * Annotations relevant to the property. These may be copied from another source. For example, if this is a getter
     * it may contain the annotations on the backing private field.
     */
    var annotations: List<AnnotationMirror> = element.annotationMirrors
        internal set

    override fun toString(): String = "$name: $type"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as Property<*>
        if (element != other.element) return false
        return true
    }

    override fun hashCode(): Int {
        return element.hashCode()
    }

    class Field(field: VariableElement) : Property<VariableElement>(field)

    class Getter(method: ExecutableElement) : Property<ExecutableElement>(method) {

        private var stripBean: Boolean = false

        val isBean: Boolean
            get() = beanPrefix() != null

        override val name: String
            get() {
                val name = super.name
                if (stripBean) {
                    val prefix = beanPrefix()
                    if (prefix != null) {
                        return Character.toLowerCase(name[prefix.length]) + name.substring(prefix.length + 1)
                    }
                }
                return name
            }

        override val callableName: String
            get() = super.name

        override val type: TypeMirror
            get() = element.returnType

        internal fun stripBean() {
            stripBean = true
        }

        private fun beanPrefix(): String? {
            if (element.returnType.kind == TypeKind.BOOLEAN) {
                val name = super.name
                if (name.length > BEAN_PREFIX_BOOL.length && name.startsWith(BEAN_PREFIX_BOOL)) {
                    return BEAN_PREFIX_BOOL
                }
            }
            val name = super.name
            return if (name.length > BEAN_PREFIX.length && name.startsWith(BEAN_PREFIX)) BEAN_PREFIX else null
        }

        companion object {
            private const val BEAN_PREFIX_BOOL = "is"
            private const val BEAN_PREFIX = "get"
        }
    }

    abstract class Param(element: VariableElement) : Property<VariableElement>(element)

    class ConstructorParam(param: VariableElement) : Param(param)

    class BuilderParam(private val method: ExecutableElement) : Param(method.parameters[0]) {
        override val callableName: String get() = method.simpleName.toString()
    }
}
