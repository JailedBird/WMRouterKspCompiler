package cn.jailedbird.arouter.ksp.compiler

import cn.jailedbird.arouter.ksp.compiler.entity.RouteDoc
import cn.jailedbird.arouter.ksp.compiler.entity.RouteMetaKsp
import cn.jailedbird.arouter.ksp.compiler.entity.kspRawType
import cn.jailedbird.arouter.ksp.compiler.utils.Consts
import cn.jailedbird.arouter.ksp.compiler.utils.KSPLoggerWrapper
import cn.jailedbird.arouter.ksp.compiler.utils.findAnnotationWithType
import cn.jailedbird.arouter.ksp.compiler.utils.findModuleHashName
import cn.jailedbird.arouter.ksp.compiler.utils.isSubclassOf
import cn.jailedbird.arouter.ksp.compiler.utils.quantifyNameToClassName
import cn.jailedbird.arouter.ksp.compiler.utils.routeType
import cn.jailedbird.arouter.ksp.compiler.utils.typeExchange
import com.alibaba.android.arouter.facade.annotation.Autowired
import com.alibaba.android.arouter.facade.annotation.Route
import com.alibaba.android.arouter.facade.enums.RouteType
import com.alibaba.android.arouter.facade.enums.TypeKind
import com.alibaba.android.arouter.facade.model.RouteMeta
import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.serializer.SerializerFeature
import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getAnnotationsByType
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.sankuai.waimai.router.annotation.RouterPage
import com.sankuai.waimai.router.interfaces.Const
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.MUTABLE_MAP
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.WildcardTypeName
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.asTypeName
import com.squareup.kotlinpoet.ksp.KotlinPoetKspPreview
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.writeTo
import java.util.TreeSet
import kotlin.reflect.KClass

@KotlinPoetKspPreview
class RoutePageSymbolProcessorProvider : SymbolProcessorProvider {

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
            private val ROUTE_CLASS_NAME = RouterPage::class.qualifiedName!!
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
            for (element in elements) {
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

                val anno: RouterPage =
                    element.getAnnotationsByType(RouterPage::class).firstOrNull() ?: continue

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
                val handler = if (isFragment || isFragmentV4) {
                    buildFragmentHandler(element)
                } else {
                    buildHandler(isActivity, element)
                }

                val interceptors = buildInterceptors(anno.interceptors.toList())
                val p = anno.interceptors.toList()


            }

        }

        /**
         * 创建Handler。格式： <code>new FragmentTransactionHandler("FragmentName")</code>
         */
//        public CodeBlock buildFragmentHandler( Symbol.ClassSymbol cls) {
//            CodeBlock.Builder b = CodeBlock.builder();
//            b.add("new $T($S)", className(Const.FRAGMENT_HANDLER_CLASS), cls.className());
//            return b.build();
//        }
//        handler.register("/fragment/demo_fragment_1", new FragmentTransactionHandler("com.sankuai.waimai.router.demo.fragment2fragment.Demo1Fragment"), new DemoFragmentInterceptor());

        private fun buildFragmentHandler(element: KSClassDeclaration): CodeBlock {
            val codeBlock = CodeBlock.builder()
            codeBlock.add(
                "\$T(\$S)",
                Const.FRAGMENT_HANDLER_CLASS.quantifyNameToClassName(),
                element.qualifiedName?.asString()
            )
            return codeBlock.build()
        }

        private fun buildHandler(isActivity: Boolean, element: KSClassDeclaration): CodeBlock {
            val codeBlock = CodeBlock.builder()
            if (isActivity) {
                codeBlock.add("\$S", element.qualifiedName?.asString())
            } else {
                codeBlock.add("new \$T()", element)
            }
            return codeBlock.build()
        }

        private fun buildInterceptors(list: List<KClass<*>>?): CodeBlock {
            val codeBlock = CodeBlock.builder()
            if (!list.isNullOrEmpty()) {
            
            }
            /*public CodeBlock buildInterceptors(List<? extends TypeMirror> interceptors) {
                    CodeBlock.Builder b = CodeBlock.builder();
                    if (interceptors != null && interceptors.size() > 0) {
                        for (TypeMirror type : interceptors) {
                            if (type instanceof Type.ClassType) {
                                Symbol.TypeSymbol e = ((Type.ClassType) type).asElement();
                                if (e instanceof Symbol.ClassSymbol && isInterceptor(e)) {
                                    b.add(", new $T()", e);
                                }
                            }
                        }
                    }
                    return b.build();
                }*/

            return codeBlock.build()
        }


        private fun categories(
            routeMeta: RouteMeta, groupsMap: HashMap<String, TreeSet<RouteMeta>>
        ) {
            if (routeVerify(routeMeta)) {
                logger.info(">>> Start categories, group = " + routeMeta.group + ", path = " + routeMeta.path + " <<<")
                val routeMetas = groupsMap[routeMeta.group]
                if (routeMetas.isNullOrEmpty()) {
                    val routeMetaSet = sortedSetOf<RouteMeta>({ r1, r2 ->
                        try {
                            r1!!.path.compareTo(r2!!.path)
                        } catch (npe: NullPointerException) {
                            logger.error(npe.message.toString())
                            0
                        }
                    })
                    routeMetaSet.add(routeMeta)
                    groupsMap[routeMeta.group] = routeMetaSet
                } else {
                    routeMetas.add(routeMeta)
                }
            } else {
                logger.warn(">>> Route meta verify error, group is " + routeMeta.group + " <<<")
            }
        }

        private fun generateGroupFiles(
            groupsMap: HashMap<String, TreeSet<RouteMeta>>,
            providersMap: MutableMap<String, RouteMeta>,
            docSource: MutableMap<String, List<RouteDoc>>
        ) {
            val dependenciesMap: HashMap<String, MutableSet<KSFile>> = HashMap()

            val parameterName = "atlas"
            val parameterSpec = ParameterSpec.builder(
                parameterName, MUTABLE_MAP.parameterizedBy(
                    STRING, RouteMeta::class.asTypeName()
                ).copy(nullable = true)
            ).build()
            val returnState = "if($parameterName == null) { return }"

            for (entry in groupsMap) {
                if (entry.key.isEmpty() || entry.value.isEmpty()) {
                    continue
                }

                val groupName = entry.key
                val groupRouteMetas = entry.value
                val dependencies = mutableSetOf<KSFile>()
                val routeDocList = mutableListOf<RouteDoc>()
                val loadInfoFunSpecBuilder: FunSpec.Builder =
                    FunSpec.builder(Consts.METHOD_LOAD_INTO).addModifiers(KModifier.OVERRIDE)
                        .addParameter(parameterSpec)

                loadInfoFunSpecBuilder.addStatement(returnState)

                for (routeMeta in groupRouteMetas) {
                    val routeDoc = extractDocInfo(routeMeta)
                    val element = routeMeta.kspRawType
                    if (routeMeta.type == RouteType.PROVIDER) {
                        for (it in element.superTypes) {
                            val declaration = it.resolve().declaration
                            val name = declaration.qualifiedName?.asString()
                            if (declaration is KSClassDeclaration) {
                                // Its implements iProvider interface himself.
                                if (name == Consts.IPROVIDER) {
                                    providersMap[element.qualifiedName!!.asString()] = routeMeta
                                    routeDoc.addPrototype(name)
                                } else if (declaration.isSubclassOf(Consts.IPROVIDER)) {
                                    // This interface extend the IProvider, so it can be used for mark provider
                                    if (!name.isNullOrEmpty()) {
                                        providersMap[name] = routeMeta
                                        routeDoc.addPrototype(name)
                                    }
                                }
                            }
                        }
                    }
                    element.containingFile?.let {
                        dependencies.add(it)
                    }

                    extractRouteMetaParamInfoDoc(routeMeta)?.let {
                        routeDoc.params = it
                    }

                    val paramConfig = extractRouteMetaParamInfo(routeMeta) // config param config
                    loadInfoFunSpecBuilder.addStatement(
                        "$parameterName.put(%S,  %T.build(%T.${routeMeta.type}, %T::class.java, %S, %S, ${paramConfig}, ${routeMeta.priority}, ${routeMeta.extra}))",
                        routeMeta.path,
                        RouteMeta::class,
                        RouteType::class,
                        element.toClassName(),
                        routeMeta.path.lowercase(),
                        routeMeta.group.lowercase()
                    )
                    routeDoc.className = element.qualifiedName!!.asString()
                    routeDocList.add(routeDoc)
                }

                dependenciesMap[groupName] = dependencies

                val groupClassName = Consts.NAME_OF_GROUP + entry.key
                val file =
                    FileSpec.builder(Consts.PACKAGE_OF_GENERATE_FILE, groupClassName).addType(
                        TypeSpec.classBuilder(
                            ClassName(Consts.PACKAGE_OF_GENERATE_FILE, groupClassName)
                        ).addKdoc(Consts.WARNING_TIPS).addSuperinterface(IROUTE_GROUP_CLASSNAME)
                            .addFunction(loadInfoFunSpecBuilder.build()).build()
                    ).build()

                file.writeTo(codeGenerator, true, dependenciesMap[groupName]!!)
                docSource[groupName] = routeDocList
                logger.info(">>> Generated group: $groupClassName<<<")
            }
        }

        private fun generateProviderFile(providersMap: MutableMap<String, RouteMeta>) {
            if (providersMap.isEmpty()) {
                return
            }
            val parameterName = "providers"
            // MutableMap<String, RouteMeta> providers
            val parameterSpec = ParameterSpec.builder(
                parameterName, MUTABLE_MAP.parameterizedBy(
                    String::class.asClassName(), RouteMeta::class.asClassName()
                ).copy(nullable = true)
            ).build()

            val loadInfoFunSpecBuilder: FunSpec.Builder =
                FunSpec.builder(Consts.METHOD_LOAD_INTO).addModifiers(KModifier.OVERRIDE)
                    .addParameter(parameterSpec)

            loadInfoFunSpecBuilder.addStatement("if($parameterName == null) { return }")
            val groupFileDependencies = mutableSetOf<KSFile>()
            for ((key, routeMeta) in providersMap) {
                loadInfoFunSpecBuilder.addStatement(
                    "$parameterName.put(%S,  %T.build(%T.${routeMeta.type}, %T::class.java, %S, %S, ${null}, ${routeMeta.priority}, ${routeMeta.extra}))",
                    key,
                    RouteMeta::class,
                    RouteType::class,
                    routeMeta.kspRawType.toClassName(),
                    routeMeta.path,
                    routeMeta.group
                )

                routeMeta.kspRawType.containingFile?.let {
                    groupFileDependencies.add(it)
                }
            }

            val providerClassName = Consts.NAME_OF_PROVIDER + Consts.SEPARATOR + moduleHashName
            val file = FileSpec.builder(Consts.PACKAGE_OF_GENERATE_FILE, providerClassName).addType(
                TypeSpec.classBuilder(
                    ClassName(
                        Consts.PACKAGE_OF_GENERATE_FILE, providerClassName
                    )
                ).addKdoc(Consts.WARNING_TIPS).addSuperinterface(IPROVIDER_GROUP_CLASSNAME)
                    .addFunction(loadInfoFunSpecBuilder.build()).build()
            ).build()
            file.writeTo(codeGenerator, true, groupFileDependencies)
            logger.info(">>> Generated provider map, name is $providerClassName <<<")
        }

        private fun generateRootFile(
            groupsMap: HashMap<String, TreeSet<RouteMeta>>,
            docSource: MutableMap<String, List<RouteDoc>>
        ) {
            if (groupsMap.isEmpty()) {
                return
            }
            val parameterName = "routes"
            val superGroupClassName = Consts.ITROUTE_ROOT.quantifyNameToClassName()
            // MutableMap<String, Class<out IRouteGroup>>?
            val parameterSpec = ParameterSpec.builder(
                parameterName, MUTABLE_MAP.parameterizedBy(
                    String::class.asClassName(), Class::class.asClassName().parameterizedBy(
                        WildcardTypeName.producerOf(
                            Consts.IROUTE_GROUP.quantifyNameToClassName()
                        )
                    )
                ).copy(nullable = true)
            ).build()

            val loadInfoFunSpecBuilder: FunSpec.Builder =
                FunSpec.builder(Consts.METHOD_LOAD_INTO).addModifiers(KModifier.OVERRIDE)
                    .addParameter(parameterSpec)

            loadInfoFunSpecBuilder.addStatement("if($parameterName == null) { return }")
            val dependencies = mutableSetOf<KSFile>()
            for (entry in groupsMap) {
                val groupName = entry.key

                if (groupName.isEmpty() || entry.value.isEmpty()) {
                    continue
                }

                loadInfoFunSpecBuilder.addStatement(
                    "$parameterName.put(%S, %T::class.java)", groupName, ClassName(
                        Consts.PACKAGE_OF_GENERATE_FILE, Consts.NAME_OF_GROUP + groupName
                    )
                )

                for (item in entry.value) {
                    item.kspRawType.containingFile?.let {
                        dependencies.add(it)
                    }
                }
            }

            val rootClassName = Consts.NAME_OF_ROOT + Consts.SEPARATOR + moduleHashName

            val file = FileSpec.builder(Consts.PACKAGE_OF_GENERATE_FILE, rootClassName).addType(
                TypeSpec.classBuilder(
                    ClassName(
                        Consts.PACKAGE_OF_GENERATE_FILE, rootClassName
                    )
                ).addKdoc(Consts.WARNING_TIPS).addSuperinterface(superGroupClassName)
                    .addFunction(loadInfoFunSpecBuilder.build()).build()
            ).build()
            file.writeTo(codeGenerator, true, dependencies)
            logger.info(">>> Generated root, name is $rootClassName <<<")
            generateDocFile(docSource, dependencies)
        }

        @Suppress("SpellCheckingInspection")
        private fun generateDocFile(
            docSource: MutableMap<String, List<RouteDoc>>, source: MutableSet<KSFile>
        ) {
            if (generateDoc) {
                val doc = JSON.toJSONString(docSource, SerializerFeature.PrettyFormat)
                val dependencies = Dependencies(false, *source.toTypedArray())
                val file = codeGenerator.createNewFile(
                    dependencies,
                    Consts.PACKAGE_OF_GENERATE_DOCS,
                    "arouter-map-of-$moduleHashName",
                    "json"
                )
                file.use {
                    it.write(doc.toByteArray())
                }
                logger.info(">>> Generated doc, name is arouter-map-of-$moduleHashName <<<")
            }
        }

        private fun routeVerify(meta: RouteMeta): Boolean {
            val path = meta.path
            // The path must be start with '/' and not empty!
            if (path.isNullOrEmpty() || !path.startsWith("/")) {
                return false
            }
            return if (meta.group.isNullOrEmpty()) { // Use default group(the first word in path)
                try {
                    val defaultGroup = path.substring(1, path.indexOf("/", 1))
                    if (defaultGroup.isEmpty()) {
                        return false
                    }
                    meta.group = defaultGroup
                    true
                } catch (e: Exception) {
                    logger.error("Failed to extract default group! " + e.message)
                    false
                }
            } else true
        }

        private fun extractRouteMeta(element: KSClassDeclaration): RouteMeta {
            val qualifiedName = element.qualifiedName?.asString()
                ?: error("local variable can not be annotated with @Route")
            val route = element.findAnnotationWithType<Route>()
                ?: error("$qualifiedName must annotated with @Route")

            return when (val routeType = element.routeType) {
                RouteType.ACTIVITY, RouteType.FRAGMENT -> {
                    val label = if (routeType == RouteType.ACTIVITY) "activity" else "fragment"
                    val paramsType = HashMap<String, Int>()
                    val injectConfig = HashMap<String, Autowired>()
                    injectParamCollector(element, paramsType, injectConfig)
                    logger.info(">>> Found $label route: $qualifiedName <<<")
                    RouteMetaKsp.build(route, element, routeType, paramsType).also {
                        it.injectConfig = injectConfig
                    }
                }

                RouteType.SERVICE -> {
                    logger.info(">>> Found service route: $qualifiedName <<<")
                    RouteMetaKsp.build(route, element, RouteType.SERVICE, null)
                }

                RouteType.PROVIDER -> {
                    logger.info(">>> Found provider route: $qualifiedName <<<")
                    RouteMetaKsp.build(route, element, RouteType.PROVIDER, null)
                }

                else -> {
                    throw RuntimeException("The @Route is marked on unsupported class, look at [${qualifiedName}].")
                }
            }
        }

        private fun extractRouteMetaParamInfo(routeMeta: RouteMeta): String {
            val builder = StringBuilder()
            val paramsType = routeMeta.paramsType
            if (!paramsType.isNullOrEmpty()) {
                // such as: mutableMapOf("a" to 1, "b" to 2) , Map has been imported
                builder.append("mutableMapOf(")
                for (types in paramsType) {
                    builder.append("\"").append(types.key).append("\" to ").append(types.value)
                        .append(", ")
                }
                builder.trimEnd(',')
                builder.append(")")
            }
            return builder.toString().ifEmpty { "null" }
        }

        private fun extractDocInfo(routeMeta: RouteMeta): RouteDoc {
            val routeDoc = RouteDoc()
            routeDoc.group = routeMeta.group
            routeDoc.path = routeMeta.path
            routeDoc.description = routeMeta.name
            routeDoc.type = routeMeta.type.name.lowercase()
            routeDoc.mark = routeMeta.extra
            return routeDoc
        }

        private fun extractRouteMetaParamInfoDoc(routeMeta: RouteMeta): MutableList<RouteDoc.Param>? {
            val paramsType = routeMeta.paramsType
            val injectConfigs = routeMeta.injectConfig
            if (!paramsType.isNullOrEmpty()) {
                val paramList = mutableListOf<RouteDoc.Param>()
                for (types in paramsType) {
                    val param = RouteDoc.Param()
                    val injectConfig = injectConfigs[types.key]
                    param.key = types.key
                    param.type = TypeKind.values()[types.value].name.lowercase()
                    param.description = injectConfig?.desc
                    param.isRequired = injectConfig?.required == true
                    paramList.add(param)
                }
                return paramList
            }
            return null
        }

        private fun injectParamCollector(
            element: KSClassDeclaration,
            paramsType: HashMap<String, Int>,
            injectConfig: HashMap<String, Autowired>
        ) {
            for (field in element.getAllProperties()) {
                val annotation = field.findAnnotationWithType<Autowired>()
                if (annotation != null && !field.isSubclassOf(Consts.IPROVIDER)) {
                    val injectName = annotation.name.ifEmpty { field.simpleName.asString() }
                    paramsType[injectName] = field.typeExchange()
                    injectConfig[injectName] = annotation
                }
            }
        }
    }

}
