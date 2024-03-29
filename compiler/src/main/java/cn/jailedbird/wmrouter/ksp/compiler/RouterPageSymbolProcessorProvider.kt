package cn.jailedbird.wmrouter.ksp.compiler

import cn.jailedbird.wmrouter.ksp.compiler.utils.KSPLoggerWrapper
import cn.jailedbird.wmrouter.ksp.compiler.utils.WMRouterHelper
import cn.jailedbird.wmrouter.ksp.compiler.utils.WMRouterHelper.findModuleID
import cn.jailedbird.wmrouter.ksp.compiler.utils.findAnnotationWithType
import cn.jailedbird.wmrouter.ksp.compiler.utils.isAbstract
import cn.jailedbird.wmrouter.ksp.compiler.utils.isSubclassOf
import cn.jailedbird.wmrouter.ksp.compiler.utils.quantifyNameToClassName
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.sankuai.waimai.router.annotation.RouterPage
import com.sankuai.waimai.router.interfaces.Const
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.ksp.KotlinPoetKspPreview

@KotlinPoetKspPreview
class RouterPageSymbolProcessorProvider : SymbolProcessorProvider {

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
        companion object {
            private val ROUTE_CLASS_NAME = RouterPage::class.qualifiedName!!
            private const val FRAGMENT_ANDROID_X_CLASS = "androidx.fragment.app.Fragment"
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
                Const.FRAGMENT_CLASS,
                Const.FRAGMENT_V4_CLASS,
                FRAGMENT_ANDROID_X_CLASS
            )
            for (element in elements) {
                if (element.isAbstract()) {
                    continue
                }
                val type: Int = element.isSubclassOf(parentTypeList)
                val isActivity: Boolean = type == 0
                val isHandler: Boolean = type == 1
                val isFragment: Boolean = type == 2
                val isFragmentV4: Boolean = type == 3 || type == 4

                if (!isActivity && !isHandler && !isFragment && !isFragmentV4) {
                    continue
                }
                val page: RouterPage =
                    element.findAnnotationWithType<RouterPage>() ?: continue

                element.containingFile?.let {
                    dependencies.add(it)
                }
                val handler = if (isFragment || isFragmentV4) {
                    buildFragmentHandler(element)
                } else {
                    WMRouterHelper.buildHandler(isActivity, element)
                }

                val interceptors = WMRouterHelper.buildInterceptors { page.interceptors.asList() }

                logger.info(">>> Found routes, ${element.qualifiedName?.asString()}")
                for (path in page.path) {
                    logger.info(">>> \tpath is $path")
                    codeBlock.addStatement(
                        "handler.register(%S, %L %L)",
                        path,
                        handler,
                        interceptors
                    )
                }
            }

            val genClassName = "PageAnnotationInit" + Const.SPLITTER + moduleHashName

            WMRouterHelper.buildHandlerInitClass(
                codeBlock.build(),
                genClassName,
                Const.PAGE_ANNOTATION_HANDLER_CLASS,
                Const.PAGE_ANNOTATION_INIT_CLASS,
                codeGenerator,
                dependencies
            )

            val fullImplName = Const.GEN_PKG + Const.DOT + genClassName
            val className =
                "ServiceInit" + Const.SPLITTER + "PageAnnotation" + Const.SPLITTER + moduleHashName
            val interfaceName = Const.PAGE_ANNOTATION_INIT_CLASS
            ServiceInitClassBuilder(className)
                .putDirectly(interfaceName, fullImplName, fullImplName, false)
                .build(codeGenerator, dependencies)
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

    }

}

