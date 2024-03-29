package cn.jailedbird.wmrouter.ksp.compiler.utils

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getAnnotationsByType
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSNode
import com.google.devtools.ksp.symbol.KSTypeReference
import com.google.devtools.ksp.symbol.Modifier
import com.squareup.kotlinpoet.ClassName

internal fun KSClassDeclaration.isAbstract(): Boolean {
    return this.modifiers.contains(Modifier.ABSTRACT)
}

@OptIn(KspExperimental::class)
internal inline fun <reified T : Annotation> KSAnnotated.findAnnotationWithType(): T? {
    return getAnnotationsByType(T::class).firstOrNull()
}

@Suppress("unused")
internal inline fun KSPLogger.check(condition: Boolean, message: () -> String) {
    check(condition, null, message)
}

internal inline fun KSPLogger.check(condition: Boolean, element: KSNode?, message: () -> String) {
    if (!condition) {
        error(message(), element)
    }
}

/**
 * Judge whether a class [KSClassDeclaration] is a subclass of another class [superClassName]
 * https://www.raywenderlich.com/33148161-write-a-symbol-processor-with-kotlin-symbol-processing
 * */
internal fun KSClassDeclaration.isSubclassOf(
    superClassName: String,
): Boolean {
    val superClasses = superTypes.toMutableList()
    while (superClasses.isNotEmpty()) {
        val current: KSTypeReference = superClasses.first()
        val declaration: KSDeclaration = current.resolve().declaration
        when {
            declaration is KSClassDeclaration && declaration.qualifiedName?.asString() == superClassName -> {
                return true
            }

            declaration is KSClassDeclaration -> {
                superClasses.removeAt(0)
                superClasses.addAll(0, declaration.superTypes.toList())
            }

            else -> {
                superClasses.removeAt(0)
            }
        }
    }
    return false
}

internal fun KSClassDeclaration.isSubclassOf(superClassNames: List<String>): Int {
    val superClasses = superTypes.toMutableList()
    while (superClasses.isNotEmpty()) {
        val current: KSTypeReference = superClasses.first()
        val declaration: KSDeclaration = current.resolve().declaration
        when {
            declaration is KSClassDeclaration && (superClassNames.indexOf(declaration.qualifiedName?.asString())) != -1 -> {
                return superClassNames.indexOf(declaration.qualifiedName?.asString())
            }

            declaration is KSClassDeclaration -> {
                superClasses.removeAt(0)
                superClasses.addAll(0, declaration.superTypes.toList())
            }

            else -> {
                superClasses.removeAt(0)
            }
        }
    }
    return -1
}

/*internal fun KSPropertyDeclaration.isSubclassOf(superClassName: String): Boolean {
    val propertyType = type.resolve().declaration
    return if (propertyType is KSClassDeclaration) {
        propertyType.isSubclassOf(superClassName)
    } else {
        false
    }
}*/

/*internal fun KSPropertyDeclaration.isSubclassOf(superClassNames: List<String>): Int {
    val propertyType = type.resolve().declaration
    return if (propertyType is KSClassDeclaration) {
        propertyType.isSubclassOf(superClassNames)
    } else {
        -1
    }
}*/

internal fun String.quantifyNameToClassName(): ClassName {
    val index = lastIndexOf(".")
    return ClassName(substring(0, index), substring(index + 1, length))
}