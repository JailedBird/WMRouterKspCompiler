package cn.jailedbird.wmrouter.ksp.compiler

import cn.jailedbird.wmrouter.ksp.compiler.utils.KSPLoggerWrapper
import cn.jailedbird.wmrouter.ksp.compiler.utils.WMRouterHelper
import cn.jailedbird.wmrouter.ksp.compiler.utils.WMRouterHelper.findModuleID
import cn.jailedbird.wmrouter.ksp.compiler.utils.findAnnotationWithType
import cn.jailedbird.wmrouter.ksp.compiler.utils.isAbstract
import cn.jailedbird.wmrouter.ksp.compiler.utils.isSubclassOf
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.sankuai.waimai.router.annotation.RouterUri
import com.sankuai.waimai.router.interfaces.Const
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.ksp.KotlinPoetKspPreview

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
        companion object {
            private val ROUTE_CLASS_NAME = RouterUri::class.qualifiedName!!
        }

        private val moduleHashName = options.findModuleID(logger)

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

        private fun parse(elements: List<KSClassDeclaration>) {
            logger.info(">>> Found routes, size is " + elements.size + " <<<")
            val codeBlock = CodeBlock.builder()
            val dependencies = mutableSetOf<KSFile>()
            val parentTypeList = listOf(
                Const.ACTIVITY_CLASS,
                Const.URI_HANDLER_CLASS,
            )
            for (element in elements) {
                if (element.isAbstract()) {
                    continue
                }
                val type: Int =
                    element.isSubclassOf(parentTypeList)
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
                val handler = WMRouterHelper.buildHandler(isActivity, element)

                val interceptors = WMRouterHelper.buildInterceptors { uri.interceptors.asList() }

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
            val interfaceName = Const.URI_ANNOTATION_INIT_CLASS
            WMRouterHelper.buildHandlerInitClass(
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

    }

}

