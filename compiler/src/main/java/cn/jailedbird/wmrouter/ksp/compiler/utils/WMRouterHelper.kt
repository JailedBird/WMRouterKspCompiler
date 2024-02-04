@file:OptIn(KotlinPoetKspPreview::class)

package cn.jailedbird.wmrouter.ksp.compiler.utils

import com.google.devtools.ksp.KSTypesNotPresentException
import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSType
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

internal object WMRouterHelper {
    private const val NO_MODULE_NAME_TIPS_KSP =
        "These no module ID for WMRouter, at 'build.gradle', like :\n" +
                "ksp {\n" +
                "    arg(\"WM_ROUTER_ID\", \"module_app\") {\n" +
                "}\n" +
                "Notice: different module's WM_ROUTER_ID need to be unique!\n"
    private const val WM_ROUTER_ID = "WM_ROUTER_ID"

    fun Map<String, String>.findModuleID(logger: KSPLogger): String {
        val name = this[WM_ROUTER_ID]
        return if (!name.isNullOrEmpty()) {
            @Suppress("RegExpSimplifiable")
            name.replace("[^0-9a-zA-Z_]+".toRegex(), "")
        } else {
            logger.error(NO_MODULE_NAME_TIPS_KSP)
            throw RuntimeException(NO_MODULE_NAME_TIPS_KSP)
        }
    }

    @OptIn(KotlinPoetKspPreview::class)
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
            block.invoke() // Notice Custom Interceptor class must be throw KSTypesNotPresentException
        } catch (e: KSTypesNotPresentException) {
            e.ksTypes
        }
        for (interceptor in interceptors) {
            if (interceptor is KSType) {
                val declaration = interceptor.declaration
                if (declaration is KSClassDeclaration) {
                    if (!declaration.isAbstract() && declaration.isSubclassOf(Const.URI_INTERCEPTOR_CLASS)) {
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