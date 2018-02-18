package me.tatarka.valueprocessor

import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.ProcessingEnvironment
import javax.annotation.processing.RoundEnvironment
import javax.annotation.processing.SupportedAnnotationTypes
import javax.lang.model.element.Element
import javax.lang.model.element.TypeElement

@SupportedAnnotationTypes("me.tatarka.valueprocessor.Target")
class ElementExtractor(private val f: (ProcessingEnvironment, Element) -> Unit) : AbstractProcessor() {
    private lateinit var env: ProcessingEnvironment

    override fun init(env: ProcessingEnvironment) {
        super.init(env)
        this.env = env
    }

    override fun process(annotations: MutableSet<out TypeElement>, env: RoundEnvironment): Boolean {
        annotations.onEach { env.getElementsAnnotatedWith(it).onEach { f(this.env, it) } }
        return false
    }
}