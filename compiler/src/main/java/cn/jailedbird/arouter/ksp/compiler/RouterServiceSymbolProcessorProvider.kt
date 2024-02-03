package cn.jailedbird.arouter.ksp.compiler

import cn.jailedbird.arouter.ksp.compiler.Helper.findModuleHashName
import cn.jailedbird.arouter.ksp.compiler.utils.KSPLoggerWrapper
import cn.jailedbird.arouter.ksp.compiler.utils.findAnnotationWithType
import cn.jailedbird.arouter.ksp.compiler.utils.isAbstract
import cn.jailedbird.arouter.ksp.compiler.utils.isSubclassOf
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
import com.sankuai.waimai.router.annotation.RouterService
import com.sankuai.waimai.router.interfaces.Const
import com.sankuai.waimai.router.service.ServiceImpl
import com.squareup.kotlinpoet.ksp.KotlinPoetKspPreview
import kotlin.math.log

@KotlinPoetKspPreview
class RouterServiceSymbolProcessorProvider : SymbolProcessorProvider {

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
            private val ROUTE_CLASS_NAME = RouterService::class.qualifiedName!!
        }

        private val moduleHashName = options.findModuleHashName(logger)
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

        @OptIn(KspExperimental::class)
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

                val interfacesAny: List<Any> = try { // KSTypesNotPresentException will be thrown
                    logger.info("Notice!!! System class will be resolved ${service.interfaces.asList()} ")
                    service.interfaces.asList()
                } catch (e: KSTypesNotPresentException) {
                    e.ksTypes
                }
                val typeMirrors = mutableSetOf<KSClassDeclaration>()
                for (i in interfacesAny) {
                    if (i is KSType) {
                        val declaration = i.declaration
                        if (declaration is KSClassDeclaration) {
                            typeMirrors.add(declaration)
                        }
                    }
                }

                val keys = service.key
                val implementationName: String = element.qualifiedName?.asString()!!
                val singleton = service.singleton
                val defaultImpl = service.defaultImpl
                val elementName = element.qualifiedName!!.asString()
                logger.info("fuckyou $elementName")
                if (elementName.contains("TestPathService1")) {

                }
                for (mirror in typeMirrors) {
                    val interfaceName: String = mirror.qualifiedName!!.asString()
                    logger.info("\tfuck $elementName -- > $interfaceName")
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
                        entity.put(null, implementationName, singleton);
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

    /*val contents: List<String>
        get() {
            val list: MutableList<String> = ArrayList()
            for (impl in mMap.values) {
                list.add(impl.toConfig())
            }
            return list
        }*/
}


