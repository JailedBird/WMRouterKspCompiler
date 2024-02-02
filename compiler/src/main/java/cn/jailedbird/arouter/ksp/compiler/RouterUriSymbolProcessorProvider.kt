package cn.jailedbird.arouter.ksp.compiler

import cn.jailedbird.arouter.ksp.compiler.utils.Consts
import cn.jailedbird.arouter.ksp.compiler.utils.KSPLoggerWrapper
import cn.jailedbird.arouter.ksp.compiler.utils.findAnnotationWithType
import cn.jailedbird.arouter.ksp.compiler.utils.findModuleHashName
import cn.jailedbird.arouter.ksp.compiler.utils.isSubclassOf
import cn.jailedbird.arouter.ksp.compiler.utils.quantifyNameToClassName
import com.google.devtools.ksp.KSTypesNotPresentException
import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSType
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

@KotlinPoetKspPreview
class RouterUriSymbolProcessorProvider : SymbolProcessorProvider {

    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return RouterUriSymbolProcessor(
            KSPLoggerWrapper(environment.logger), environment.codeGenerator, environment.options
        )
    }

    class RouterUriSymbolProcessor(
        private val logger: KSPLoggerWrapper,
        private val codeGenerator: CodeGenerator,
        options: Map<String, String>
    ) : SymbolProcessor {
        @Suppress("SpellCheckingInspection")
        companion object {
            private val ROUTE_CLASS_NAME = RouterUri::class.qualifiedName!!
            private val IROUTE_GROUP_CLASSNAME = Consts.IROUTE_GROUP.quantifyNameToClassName()
            private val IPROVIDER_GROUP_CLASSNAME = Consts.IPROVIDER_GROUP.quantifyNameToClassName()
        }

        private val moduleHashName = options.findModuleHashName(logger)
        private val generateDoc = Consts.VALUE_ENABLE == options[Consts.KEY_GENERATE_DOC_NAME]

        override fun process(resolver: Resolver): List<KSAnnotated> {
            val symbol = resolver.getSymbolsWithAnnotation(ROUTE_CLASS_NAME)

            val elements = symbol.filterIsInstance<KSClassDeclaration>().toList()

            if (elements.isNotEmpty()) {
                logger.info(">>> RoutePageSymbolProcessor init. <<<")
                try {
                    parse(elements)
                } catch (e: Exception) {
                    logger.exception(e)
                }
            }

            return emptyList()
        }


        @OptIn(KspExperimental::class)
        private fun parse(elements: List<KSClassDeclaration>) {
            logger.info(">>> Found routes, size is " + elements.size + " <<<")
            val codeBlock = CodeBlock.builder()
            val dependencies = mutableSetOf<KSFile>()
            for (element in elements) {
                // Judge is Abstract class
                val isAbstract: Boolean =
                    element.modifiers.contains(com.google.devtools.ksp.symbol.Modifier.ABSTRACT)
                if (isAbstract) {
                    continue
                }
                val type: Int =
                    element.isSubclassOf(
                        listOf(
                            Const.ACTIVITY_CLASS,
                            Const.URI_HANDLER_CLASS,
                        )
                    )
                val isActivity: Boolean = type == 0
                val isHandler: Boolean = type == 1

                if (!isActivity && !isHandler) {
                    continue
                }
                val uri: RouterUri =
                    element.findAnnotationWithType<RouterUri>() ?: continue

                element.containingFile?.let {
                    dependencies.add(it)
                }
                val handler = buildHandler(isActivity, element)

                val interceptors = buildInterceptors(uri)


                /*
                * String[] pathList = page.path();
                    for (String path : pathList) {
                        builder.addStatement("handler.register($S, $L$L)",
                                path,
                                handler,
                                interceptors);
                    }*/
                // 此处类型转换存在错误
                // https://github.com/google/ksp/issues/1329
                // If java annotation value is array type, it will make getAnnotationsByType throw class cast issue, it seems that in java we can declare a single value for annotation value whose type is array, like this one
                // https://github.com/google/ksp/pull/1330
                // java.lang.ClassCastException: class java.lang.String cannot be cast to class [Ljava.lang.String; (java.lang.String and [Ljava.lang.String; are in module java.base of loader 'bootstrap')

                logger.info(">>> Found routes, ${element.qualifiedName?.asString()}")
                for (path in uri.path) {
                    logger.info(">>> \tpath is $path")
                    codeBlock.addStatement(
                        "handler.register(%S, %S, %S, %L, %L%L)",
                        uri.scheme,
                        uri.host,
                        path,
                        handler,
                        uri.exported,
                        interceptors
                    )
                }
            }


            val genClassName = "UriAnnotationInit" + Const.SPLITTER + moduleHashName
            // val handlerClassName = Const.PAGE_ANNOTATION_HANDLER_CLASS
            val interfaceName = Const.PAGE_ANNOTATION_INIT_CLASS
            Helper.buildHandlerInitClass(
                codeBlock.build(),
                genClassName,
                Const.URI_ANNOTATION_HANDLER_CLASS,
                Const.URI_ANNOTATION_INIT_CLASS,
                codeGenerator,
                dependencies
            )

            val fullImplName = Const.GEN_PKG + Const.DOT + genClassName
            val className =
                "ServiceInit" + Const.SPLITTER + "UriAnnotation" + Const.SPLITTER + moduleHashName
            ServiceInitClassBuilder(className)
                .putDirectly(interfaceName, fullImplName, fullImplName, false)
                .build(codeGenerator, dependencies)
        }


        private fun generatePageAnnotationInitFile(
            methodCodeBlock: CodeBlock,
            genClassName: String,
            dependencies: Iterable<KSFile>
        ) {

        }


        private fun buildFragmentHandler(element: KSClassDeclaration): CodeBlock {
            val codeBlock = CodeBlock.builder()
            codeBlock.add(
                "%T(%S)",
                Const.FRAGMENT_HANDLER_CLASS.quantifyNameToClassName(),
                element.qualifiedName?.asString()
            )
            return codeBlock.build()
        }

        private fun buildHandler(isActivity: Boolean, element: KSClassDeclaration): CodeBlock {
            val codeBlock = CodeBlock.builder()
            if (isActivity) {
                codeBlock.add("%S", element.qualifiedName?.asString())
            } else {
                codeBlock.add("%T()", element.toClassName())
            }
            return codeBlock.build()
        }

        @OptIn(KspExperimental::class)
        private fun buildInterceptors(page: RouterUri): CodeBlock {
            val codeBlock = CodeBlock.builder()
            val interceptors: List<Any> = try { // KSTypesNotPresentException will be thrown
                page.interceptors.asList()
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
                            logger.info("fuck ${declaration.toClassName()}")
                            codeBlock.add(", %T()", declaration.toClassName())
                        }
                    }
                }
            }
            return codeBlock.build()
        }


    }

}

