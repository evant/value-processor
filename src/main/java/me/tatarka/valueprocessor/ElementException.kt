package me.tatarka.valueprocessor

import javax.annotation.processing.Messager
import javax.lang.model.element.Element
import javax.tools.Diagnostic

class ElementException(private val messages: List<Message>) :
    Exception(messages.joinToString("\n", transform = Message::message)) {

    constructor(message: String, element: Element?) : this(listOf(Message(message, element)))

    fun printMessage(messager: Messager) {
        for ((message, element) in messages) {
            if (element != null) {
                messager.printMessage(Diagnostic.Kind.ERROR, message, element)
            } else {
                messager.printMessage(Diagnostic.Kind.ERROR, message)
            }
        }
    }

    data class Message(val message: String, val element: Element? = null)
}
