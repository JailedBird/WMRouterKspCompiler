package cn.jailedbird.arouter.ksp.compiler

import cn.jailedbird.arouter.ksp.compiler.utils.Consts
import cn.jailedbird.arouter.ksp.compiler.utils.quantifyNameToClassName
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.symbol.KSFile
import com.sankuai.waimai.router.interfaces.Const
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.ksp.KotlinPoetKspPreview
import com.squareup.kotlinpoet.ksp.writeTo

object Helper {
    @OptIn(KotlinPoetKspPreview::class)
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
                        .addKdoc(Consts.WARNING_TIPS)
                        .addSuperinterface(superInterfaceClassName.quantifyNameToClassName())
                        .addFunction(initMethod)
                        .build()
                )
                .build()

        file.writeTo(codeGenerator, true, dependencies)

    }

}