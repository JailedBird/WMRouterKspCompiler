package cn.jailedbird.arouter.ksp.compiler

import cn.jailedbird.arouter.ksp.compiler.utils.quantifyNameToClassName
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.symbol.KSFile
import com.sankuai.waimai.router.interfaces.Const
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.ksp.KotlinPoetKspPreview
import com.squareup.kotlinpoet.ksp.writeTo

class ServiceInitClassBuilder(
    private val className: String,
    private val builder: CodeBlock.Builder = CodeBlock.builder()
) {
    companion object {
        val serviceLoaderClass = Const.SERVICE_LOADER_CLASS.quantifyNameToClassName()
    }

    fun put(
        interfaceName: String,
        key: String,
        implementName: String,
        singleton: Boolean
    ): ServiceInitClassBuilder {
        builder.addStatement(
            "%T.put(%T::class.java, %S, %T::class.java, %L)",
            serviceLoaderClass,
            interfaceName.quantifyNameToClassName(),
            key,
            implementName.quantifyNameToClassName(),
            singleton
        )
        return this
    }

    fun putDirectly(
        interfaceName: String,
        key: String?,
        implementName: String,
        singleton: Boolean
    ): ServiceInitClassBuilder {
        builder.addStatement(
            "%T.put(%T::class.java, %S, %L::class.java, %L)",
            serviceLoaderClass,
            interfaceName.quantifyNameToClassName(),
            key,
            implementName,
            singleton
        )
        return this
    }


    @OptIn(KotlinPoetKspPreview::class)
    fun build(codeGenerator: CodeGenerator, dependencies: Iterable<KSFile>) {
        val methodSpec =
            FunSpec.builder(Const.INIT_METHOD)
                .addModifiers(KModifier.PUBLIC)
                .addCode(builder.build()).build()
        val file =
            FileSpec.builder(Const.GEN_PKG_SERVICE, className)
                .addType(
                    TypeSpec.objectBuilder(ClassName(Const.GEN_PKG_SERVICE, className))
                        .addModifiers(KModifier.PUBLIC)
                        .addFunction(methodSpec)
                        .build()
                ).build()

        file.writeTo(codeGenerator = codeGenerator, true, dependencies)
    }

}