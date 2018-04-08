package me.tatarka.valueprocessor

import java.util.*
import javax.lang.model.element.*
import javax.lang.model.type.TypeKind
import javax.lang.model.type.TypeMirror
import javax.lang.model.util.ElementFilter
import javax.lang.model.util.Types
import kotlin.collections.ArrayList

/**
 * The properties of the value object. You can either iterator over these directly (ex: `for (Property<?> property: properties)`)
 * or iterate over a specific sub-set (ex: `for (Property.Field field : properties.getFields())`).
 */
class Properties internal constructor(
    private val names: List<Property<*>>,
    /**
     * All parameters, this includes constructor/factory params and builder methods.
     */
    val params: List<Property.Param>,
    /**
     * All accessors, this includes accessible fields and getters.
     */
    val accessors: List<Property.Accessor<*>>,
    /**
     * All accessible fields
     */
    val fields: List<Property.Field>,
    /**
     * All getters
     */
    val getters: List<Property.Getter>,
    /**
     * All constructor/factory params
     */
    val constructorParams: List<Property.ConstructorParam>,
    /**
     * All builder methods.
     */
    val builderParams: List<Property.BuilderParam>
) : List<Property<*>> by names {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as Properties
        if (names != other.names) return false
        return true
    }

    override fun hashCode(): Int {
        return names.hashCode()
    }

    override fun toString(): String {
        return names.toString()
    }

    private class Builder(val types: Types) {
        val names = mutableListOf<Property<*>>()
        val params = mutableListOf<Property.Param>()
        val accessors = mutableListOf<Property.Accessor<*>>()
        val getters = mutableListOf<Property.Getter>()
        val fields = mutableListOf<Property.Field>()
        val constructorParams = mutableListOf<Property.ConstructorParam>()
        val builderParams = mutableListOf<Property.BuilderParam>()

        fun addFieldsAndGetters(
            targetClass: TypeElement,
            platformGetters: MutableList<Property.Getter>,
            platformFields: MutableList<Property.Field>
        ) {
            for (superInterface in targetClass.interfaces) {
                addFieldsAndGetters(types.asElement(superInterface) as TypeElement, platformGetters, platformFields)
            }

            val superclass = targetClass.superclass
            if (superclass.kind != TypeKind.NONE) {
                addFieldsAndGetters(
                    types.asElement(targetClass.superclass) as TypeElement,
                    platformGetters,
                    platformFields
                )
            }

            val platformType = isPlatformType(targetClass.asType())

            // fields
            for (field in ElementFilter.fieldsIn(targetClass.enclosedElements)) {
                if (platformFields.containsField(field)) continue
                if (platformType) {
                    field.tryToField()?.let(platformFields::add)
                } else if (!platformFields.containsField(field)) {
                    field.tryToField()?.let {
                        fields.add(it)
                        accessors.add(it)
                    }
                }
            }

            // getters
            for (method in ElementFilter.methodsIn(targetClass.enclosedElements)) {
                if (platformGetters.containsGetter(method)) continue
                if (platformType) {
                    method.tryToGetter(targetClass)?.let(platformGetters::add)
                } else if (!getters.containsGetter(method)) {
                    method.tryToGetter(targetClass)?.let {
                        getters.add(it)
                        accessors.add(it)
                    }
                }
            }
        }

        fun ExecutableElement.tryToGetter(classElement: TypeElement): Property.Getter? {
            if (modifiers.contains(Modifier.PRIVATE) ||
                modifiers.contains(Modifier.STATIC) ||
                returnType.kind == TypeKind.VOID ||
                !parameters.isEmpty() ||
                isMethodToSkip(classElement, this)
            ) {
                return null
            }
            return Property.Getter(this)
        }

        fun VariableElement.tryToField(): Property.Field? {
            if (modifiers.contains(Modifier.STATIC)) {
                return null
            }
            return Property.Field(this)
        }

        fun List<Property.Field>.containsField(field: VariableElement): Boolean {
            val name = field.simpleName.toString()
            return find { it.name == name } != null
        }

        fun List<Property.Getter>.containsGetter(getter: ExecutableElement): Boolean {
            val name = getter.simpleName.toString()
            return find { it.name == name } != null
        }

        fun addConstructorParam(param: VariableElement) {
            val name = Property.ConstructorParam(param)
            constructorParams.add(name)
            params.add(name)
        }

        fun addBuilderParam(builderType: TypeMirror, method: ExecutableElement) {
            if (method.returnType == builderType && method.parameters.size == 1) {
                val name = Property.BuilderParam(method)
                builderParams.add(name)
                params.add(name)
            }
        }

        fun build(): Properties {
            stripBeans(getters)
            removeExtraBuilders()
            removeGettersForTransientFields()
            mergeSerializeNames(params, fields, getters)
            removeExtraFields()
            names.addAll(params)
            fields
                .filterNot { containsName(names, it) }
                .forEach { names.add(it) }
            getters
                .filterNot { containsName(names, it) }
                .forEach { names.add(it) }
            return Properties(names, params, accessors, fields, getters, constructorParams, builderParams)
        }

        private fun stripBeans(getters: List<Property.Getter>) {
            val allBeans = getters.all { it.isBean }
            if (allBeans) {
                for (getter in getters) {
                    getter.stripBean()
                }
            }
        }

        private fun removeExtraBuilders() {
            for (i in builderParams.indices.reversed()) {
                val builderParam = builderParams[i]
                if (containsName(constructorParams, builderParam)) {
                    builderParams.removeAt(i)
                    params.remove(builderParam)
                }
            }
        }

        private fun removeExtraFields() {
            for (i in fields.indices.reversed()) {
                val field = fields[i]
                val modifiers = field.element.modifiers
                if (modifiers.contains(Modifier.PRIVATE)
                    || modifiers.contains(Modifier.TRANSIENT)
                    || containsName(getters, field)
                ) {
                    fields.removeAt(i)
                    accessors.remove(field)
                }
            }
        }

        private fun removeGettersForTransientFields() {
            for (i in getters.indices.reversed()) {
                val getter = getters[i]
                val field = findName(fields, getter)
                if (field != null && field.element.modifiers.contains(Modifier.TRANSIENT)) {
                    getters.removeAt(i)
                    accessors.remove(getter)
                }
            }
        }

        private fun isMethodToSkip(classElement: TypeElement, method: ExecutableElement): Boolean {
            val name = method.simpleName.toString()
            if (METHODS_TO_SKIP.contains(name)) {
                return true
            }
            return isKotlinClass(classElement) && name.matches("component[0-9]+".toRegex())
        }

    }

    internal companion object {
        private val METHODS_TO_SKIP = Arrays.asList(
            "hashCode", "toString", "clone"
        )

        private fun merge(properties: Array<Property<*>?>) {
            if (properties.isEmpty()) {
                return
            }

            var annotations: MutableList<AnnotationMirror>? = null
            for (name in properties) {
                if (name == null) {
                    continue
                }
                if (!name.annotations.isEmpty()) {
                    if (annotations == null) {
                        annotations = ArrayList(name.annotations)
                    } else {
                        for (annotation in name.annotations) {
                            if (annotations.contains(annotation)) {
                                throw ElementException("Duplicate annotation $annotation found on $name", name.element)
                            } else {
                                annotations.add(annotation)
                            }
                        }
                    }
                }
            }
            if (annotations != null) {
                for (name in properties) {
                    if (name != null) {
                        name.annotations = annotations
                    }
                }
            }
        }

        @SafeVarargs
        private fun mergeSerializeNames(vararg propertyLists: List<Property<*>>) {
            if (propertyLists.isEmpty()) {
                return
            }
            for (name in propertyLists[0]) {
                val names = arrayOfNulls<Property<*>>(propertyLists.size)
                names[0] = name
                for (i in 1 until propertyLists.size) {
                    names[i] = findName(propertyLists[i], name)
                }
                merge(names)
            }
        }

        private fun <N : Property<*>> findName(names: List<N>, property: Property<*>): N? {
            return names.firstOrNull { it.name == property.name }
        }

        private fun containsName(properties: List<Property<*>>, property: Property<*>): Boolean {
            return findName(properties, property) != null
        }

        private fun isKotlinClass(element: TypeElement): Boolean {
            return element.annotationMirrors.any { it.annotationType.toString() == "kotlin.Metadata" }
        }

        operator fun invoke(types: Types, creator: Creator): Properties {
            val builder = Builder(types)
            // constructor params
            for (param in creator.element.parameters) {
                builder.addConstructorParam(param)
            }

            if (creator is Creator.Builder) {
                val builderClass = creator.builderClass
                for (method in ElementFilter.methodsIn(builderClass.enclosedElements)) {
                    builder.addBuilderParam(builderClass.asType(), method)
                }
            }

            val targetClass = creator.targetClass
            builder.addFieldsAndGetters(targetClass, mutableListOf(), mutableListOf())

            return builder.build()
        }
    }
}
