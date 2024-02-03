@file:OptIn(KotlinPoetKspPreview::class)

package cn.jailedbird.arouter.ksp.compiler

import cn.jailedbird.arouter.ksp.compiler.utils.Consts
import cn.jailedbird.arouter.ksp.compiler.utils.isSubclassOf
import cn.jailedbird.arouter.ksp.compiler.utils.quantifyNameToClassName
import com.google.devtools.ksp.KSTypesNotPresentException
import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSType
import com.sankuai.waimai.router.annotation.RouterPage
import com.sankuai.waimai.router.annotation.RouterRegex
import com.sankuai.waimai.router.annotation.RouterService
import com.sankuai.waimai.router.annotation.RouterUri
import com.sankuai.waimai.router.interfaces.Const
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.ksp.KotlinPoetKspPreview
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.writeTo
import kotlin.reflect.KClass

object Helper {

    fun Map<String, String>.findModuleHashName(logger: KSPLogger): String {
        val name = this[Consts.KEY_MODULE_HASH_NAME]
        return if (!name.isNullOrEmpty()) {
            @Suppress("RegExpSimplifiable", "KotlinConstantConditions")
            name.replace("[^0-9a-zA-Z_]+".toRegex(), "")
        } else {
            logger.error(Consts.NO_MODULE_NAME_TIPS_KSP)
            throw RuntimeException("ARouter::Compiler >>> No module name, for more information, look at gradle log.")
        }
    }

    fun buildHandler(isActivity: Boolean, element: KSClassDeclaration): CodeBlock {
        val codeBlock = CodeBlock.builder()
        if (isActivity) {
            codeBlock.add("%S", element.qualifiedName?.asString())
        } else {
            codeBlock.add("%T()", element.toClassName())
        }
        return codeBlock.build()
    }

    @OptIn(KspExperimental::class)
    fun buildInterceptors(block: () -> List<KClass<*>>): CodeBlock {
        val codeBlock = CodeBlock.builder()
        val interceptors: List<Any> = try { // KSTypesNotPresentException will be thrown
            block.invoke()
        } catch (e: KSTypesNotPresentException) {
            e.ksTypes
        }
        for (interceptor in interceptors) {
            if (interceptor is KSType) {
                val declaration = interceptor.declaration
                if (declaration is KSClassDeclaration) {
                    if (!declaration.modifiers.contains(com.google.devtools.ksp.symbol.Modifier.ABSTRACT) &&
                        declaration.isSubclassOf(Const.URI_INTERCEPTOR_CLASS)
                    ) {
                        codeBlock.add(", %T()", declaration.toClassName())
                    }
                }
            }
        }
        return codeBlock.build()
    }

    fun buildHandlerInitClass(
        methodCodeBlock: CodeBlock,
        genClassName: String,
        handlerClassName: String,
        superInterfaceClassName: String,
        codeGenerator: CodeGenerator,
        dependencies: Iterable<KSFile>
    ) {
        val handlerParameterSpec = ParameterSpec.builder(
            "handler",
            handlerClassName.quantifyNameToClassName()
        ).build()

        val initMethod: FunSpec =
            FunSpec.builder(Const.INIT_METHOD)
                .addModifiers(KModifier.PUBLIC, KModifier.OVERRIDE)
                .addParameter(handlerParameterSpec)
                .addCode(methodCodeBlock)
                .build()

        val file =
            FileSpec.builder(Const.GEN_PKG, genClassName)
                .addType(
                    TypeSpec.classBuilder(ClassName(Const.GEN_PKG, genClassName))
                        .addSuperinterface(superInterfaceClassName.quantifyNameToClassName())
                        .addFunction(initMethod)
                        .build()
                )
                .build()

        file.writeTo(codeGenerator, true, dependencies)

    }

    @OptIn(KspExperimental::class)
    fun parseAnnotationClassParameter(block: () -> List<KClass<*>>): List<String> {
        return try { // KSTypesNotPresentException will be thrown
            block.invoke().mapNotNull { it.qualifiedName }
        } catch (e: KSTypesNotPresentException) {
            val res = mutableListOf<String>()
            val ksTypes = e.ksTypes
            for (ksType in ksTypes) {
                val declaration = ksType.declaration
                if (declaration is KSClassDeclaration) {
                    declaration.qualifiedName?.asString()?.let {
                        res.add(it)
                    }
                }
            }
            res
        }
    }

}