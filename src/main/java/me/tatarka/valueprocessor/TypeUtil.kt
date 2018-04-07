package me.tatarka.valueprocessor

import javax.lang.model.type.TypeMirror

internal fun isPlatformType(type: TypeMirror): Boolean {
    val typeName = type.toString()
    return typeName.startsWith("java.")
            || typeName.startsWith("javax.")
            || typeName.startsWith("android.")
}
