package cn.jailedbird.arouter.ksp.compiler

import cn.jailedbird.arouter.ksp.compiler.utils.Consts
import cn.jailedbird.arouter.ksp.compiler.utils.KSPLoggerWrapper
import cn.jailedbird.arouter.ksp.compiler.utils.findAnnotationWithType
import cn.jailedbird.arouter.ksp.compiler.utils.findModuleHashName
import cn.jailedbird.arouter.ksp.compiler.utils.isSubclassOf
import cn.jailedbird.arouter.ksp.compiler.utils.quantifyNameToClassName
import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.sankuai.waimai.router.annotation.RouterRegex
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

@KotlinPoetKspPreview
class RouterRegexSymbolProcessorProvider : SymbolProcessorProvider {

    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return RoutePageSymbolProcessor(
            KSPLoggerWrapper(environment.logger), environment.codeGenerator, environment.options
        )
    }

    class RoutePageSymbolProcessor(
        private val logger: KSPLoggerWrapper,
        private val codeGenerator: CodeGenerator,
        options: Map<String, String>
    ) : SymbolProcessor {
        @Suppress("SpellCheckingInspection")
        companion object {
            private val ROUTE_CLASS_NAME = RouterRegex::class.qualifiedName!!
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
                val regex: RouterRegex =
                    element.findAnnotationWithType<RouterRegex>() ?: continue

                element.containingFile?.let {
                    dependencies.add(it)
                }
                val handler = Helper.buildHandler(isActivity, element)

                val interceptors = Helper.buildInterceptors(regex)

                logger.info(">>> Found routes, ${element.qualifiedName?.asString()}")

                // regex, activityClassName/new Handler(), exported, priority, new Interceptors()
                codeBlock.addStatement(
                    "handler.register(%S, %L, %L, %L%L)",
                    regex.regex,
                    handler,
                    regex.exported,
                    regex.priority,
                    interceptors
                )
            }


            val genClassName = "RegexAnnotationInit" + Const.SPLITTER + moduleHashName

            Helper.buildHandlerInitClass(
                codeBlock.build(),
                genClassName,
                Const.REGEX_ANNOTATION_HANDLER_CLASS,
                Const.REGEX_ANNOTATION_INIT_CLASS,
                codeGenerator,
                dependencies
            )

            val fullImplName = Const.GEN_PKG + Const.DOT + genClassName
            val className =
                "ServiceInit" + Const.SPLITTER + "RegexAnnotationInit" + Const.SPLITTER + moduleHashName
            val interfaceName = Const.REGEX_ANNOTATION_INIT_CLASS
            ServiceInitClassBuilder(className)
                .putDirectly(interfaceName, fullImplName, fullImplName, false)
                .build(codeGenerator, dependencies)
        }


        private fun generatePageAnnotationInitFile(
            methodCodeBlock: CodeBlock,
            genClassName: String,
            dependencies: Iterable<KSFile>
        ) {
            val handlerParameterSpec = ParameterSpec.builder(
                "handler",
                Const.PAGE_ANNOTATION_HANDLER_CLASS.quantifyNameToClassName()
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
                            .addSuperinterface(Const.PAGE_ANNOTATION_INIT_CLASS.quantifyNameToClassName())
                            .addFunction(initMethod)
                            .build()
                    )
                    .build()

            file.writeTo(codeGenerator, true, dependencies)
        }


    }

}

