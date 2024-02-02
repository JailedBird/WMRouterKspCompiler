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
import com.squareup.kotlinpoet.ksp.toClassName
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

        private val FRAGMENT_ANDROID_X_CLASS = "androidx.fragment.app.Fragment"

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
                            Const.FRAGMENT_CLASS,
                            Const.FRAGMENT_V4_CLASS,
                            FRAGMENT_ANDROID_X_CLASS
                        )
                    )
                val isActivity: Boolean = type == 0
                val isHandler: Boolean = type == 1
                val isFragment: Boolean = type == 2
                val isFragmentV4: Boolean = type == 3 || type == 4

                if (!isActivity && !isHandler && !isFragment && !isFragmentV4) {
                    continue
                }
                val page: RouterRegex =
                    element.findAnnotationWithType<RouterRegex>() ?: continue

                /*public class PageAnnotationInit_b6d2ec00f1c180a333609129781e87f8 implements IPageAnnotationInit {
                      public void init(PageAnnotationHandler handler) {
                        handler.register("/fragment/demo_fragment_1", new FragmentTransactionHandler("com.sankuai.waimai.router.demo.fragment2fragment.Demo1Fragment"), new DemoFragmentInterceptor());
                        handler.register("/fragment/demo_fragment_2", new FragmentTransactionHandler("com.sankuai.waimai.router.demo.fragment2fragment.Demo2Fragment"), new DemoFragmentInterceptor());
                        handler.register("/test/handler", new TestPageAnnotation.TestHandler());
                        handler.register("/test/interceptor", new TestPageAnnotation.TestInterceptorHandler(), new UriParamInterceptor());
                        handler.register("/test/interceptors", new TestPageAnnotation.TestInterceptorsHandler(), new UriParamInterceptor(), new ChainedInterceptor());
                      }
                    }
                */
                element.containingFile?.let {
                    dependencies.add(it)
                }
                val handler = if (isFragment || isFragmentV4) {
                    buildFragmentHandler(element)
                } else {
                    buildHandler(isActivity, element)
                }

                val interceptors = buildInterceptors(page)


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

            Helper.buildHandlerInitClass(
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
        private fun buildInterceptors(page: RouterRegex): CodeBlock {
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

