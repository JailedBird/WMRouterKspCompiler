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
import com.sankuai.waimai.router.annotation.RouterService
import com.sankuai.waimai.router.interfaces.Const
import com.sankuai.waimai.router.service.ServiceImpl
import com.squareup.kotlinpoet.ksp.KotlinPoetKspPreview

@KotlinPoetKspPreview
class RouterServiceSymbolProcessorProvider : SymbolProcessorProvider {

    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return RouterServiceSymbolProcessor(
            KSPLoggerWrapper(environment.logger), environment.codeGenerator, environment.options
        )
    }

    class RouterServiceSymbolProcessor(
        private val logger: KSPLoggerWrapper,
        private val codeGenerator: CodeGenerator,
        options: Map<String, String>
    ) : SymbolProcessor {
        companion object {
            private val ROUTE_CLASS_NAME = RouterService::class.qualifiedName!!
        }

        private val moduleHashName = options.findModuleID(logger)
        override fun process(resolver: Resolver): List<KSAnnotated> {
            val symbol = resolver.getSymbolsWithAnnotation(ROUTE_CLASS_NAME)

            val elements = symbol.filterIsInstance<KSClassDeclaration>().toList()

            if (elements.isNotEmpty()) {
                logger.info(">>> RoutePageSymbolProcessor init. <<<")
                try {
                    val dependencies = mutableSetOf<KSFile>()
                    val maps = parse(elements, dependencies)
                    if (maps.isNotEmpty()) {
                        generateFile(maps, dependencies)
                    }
                } catch (e: Exception) {
                    logger.exception(e)
                }
            }

            return emptyList()
        }

        private fun parse(
            elements: List<KSClassDeclaration>,
            dependencies: MutableSet<KSFile>
        ): HashMap<String, Entity> {
            logger.info(">>> Found routes, size is " + elements.size + " <<<")
            val mEntityMap = hashMapOf<String, Entity>()

            for (element in elements) {
                val service: RouterService =
                    element.findAnnotationWithType<RouterService>() ?: continue

                element.containingFile?.let {
                    dependencies.add(it)
                }

                val interfaceNames =
                    WMRouterHelper.parseAnnotationClassParameter { service.interfaces.asList() }

                val keys = service.key
                val implementationName: String = element.qualifiedName?.asString()!!
                val singleton = service.singleton
                val defaultImpl = service.defaultImpl
                val elementName = element.qualifiedName!!.asString()

                for (interfaceName in interfaceNames) {
                    if (element.isAbstract() || !element.isSubclassOf(interfaceName)) {
                        val msg =
                            "The $elementName does not implement the interface $interfaceName annotated with the @RouterService annotation."
                        throw RuntimeException(msg)
                    }

                    mEntityMap.putIfAbsent(interfaceName, Entity(interfaceName))
                    val entity: Entity = mEntityMap[interfaceName]!!

                    if (defaultImpl) {
                        entity.put(ServiceImpl.DEFAULT_IMPL_KEY, implementationName, singleton)
                    }

                    if (keys.isNotEmpty()) {
                        for (key in keys) {
                            if (key.contains(":")) {
                                val msg = String.format(
                                    "%s: 注解%s的key参数不可包含冒号",
                                    implementationName, RouterService::class.java.name
                                )
                                throw RuntimeException(msg)
                            }
                            entity.put(key, implementationName, singleton)
                        }
                    } else {
                        entity.put(null, implementationName, singleton)
                    }

                }
            }
            return mEntityMap
        }

        private fun generateFile(map: HashMap<String, Entity>, dependencies: MutableSet<KSFile>) {
            val generator = ServiceInitClassBuilder("ServiceInit" + Const.SPLITTER + moduleHashName)
            for ((key, value) in map.entries) {
                for (service in value.map.values) {
                    generator.put(key, service.key, service.implementation, service.isSingleton)
                }
            }
            generator.build(codeGenerator, dependencies)
        }

    }

}


class Entity(private val mInterfaceName: String) {
    private val mMap: MutableMap<String, ServiceImpl> = HashMap()

    val map: Map<String, ServiceImpl>
        get() = mMap

    fun put(key: String?, implementationName: String, singleton: Boolean) {
        val impl = ServiceImpl(key, implementationName, singleton)
        val prev = mMap.put(impl.key, impl)
        val errorMsg = ServiceImpl.checkConflict(mInterfaceName, prev, impl)
        if (errorMsg != null) {
            throw java.lang.RuntimeException(errorMsg)
        }
    }

}


