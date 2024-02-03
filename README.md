# [WMRouterKspCompiler](https://github.com/JailedBird/WMRouterKspCompiler)

[![](https://jitpack.io/v/JailedBird/WMRouterKspCompiler.svg)](https://jitpack.io/#JailedBird/WMRouterKspCompiler)

## 项目简介

本项目是对 [美团 WMRouter](https://github.com/meituan/WMRouter) 注解处理器模块的Kotlin Symbol Processing（KSP）注解处理器适配；



## 接入方案

*PS：开发中发现低版本ksp存在严重bug，所以此项目最低ksp版本需要保证1.8.20-1.0.10+， 因此使用kotlin 1.8.20 & ksp 1.8.20-1.0.11*



0、 插件发布在[jitpack](https://jitpack.io/#JailedBird/WMRouterKspCompiler)，目前是测试阶段； 

```
maven { url 'https://jitpack.io' }
```



1、 导入ksp插件 & 添加jitpack仓库

```
plugins {
    id 'org.jetbrains.kotlin.android' version '1.8.20' apply false
    id 'org.jetbrains.kotlin.jvm' version '1.8.20' apply false
    id 'com.google.devtools.ksp' version '1.8.20-1.0.11' apply false
}
```



2、 需要使用此插件的模块，模块build.gradle需要做出如下配置

- 导入ksp插件本身

  ```
  plugins {
      id 'org.jetbrains.kotlin.android'
      id 'com.google.devtools.ksp'
  }
  ```

  

- 配置参数，这一步是原插件没有的，原插件是使用文件名称的哈希摘构成生成文件名称，但是这种存在变化的输出文件名称可能破坏ksp的增量编译特性，因此这里强制配置； 每个模块不同即可，如果相同、未配置，编译阶段就会报错；

  ```
  ksp {
      arg("WM_ROUTER_ID", project.getName())
  }
  ```

- 导入插件  

  ```
  // 本地依赖 开发阶段可以将compiler模块拷贝到具体项目 试试修复bug
  // ksp project(":compiler")
  ksp "com.github.JailedBird:WMRouterKspCompiler:1.8.20-0.0.1"
  ```

  



## 关键问题记录

1、 使用KSP解析 Java注解中的数组 会出现类型转换错误 

复现记录：e45bb04b67db7d9938c34842b86099729bd71984

修复记录：4e317601accdb062fcffa3c028b674628c592c42

问题描述：Java注解类中的数组，会被注解非数组的对象类，比如path的String数组会被解析String类，从而导致类型转换错误

```
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.CLASS)
public @interface RouterPage {
    String[] path();
    Class[] interceptors() default {};
}

val page: RouterPage =
    element.findAnnotationWithType<RouterPage>() ?: continue
    
val paths = page.path

java.lang.ClassCastException: class java.lang.String cannot be cast to class [Ljava.lang.String; (java.lang.String and [Ljava.lang.String; are in module java.base of loader 'bootstrap')
	at jdk.proxy6/jdk.proxy6.$Proxy30.path(Unknown Source)
	at cn.jailedbird.arouter.ksp.compiler.RoutePageSymbolProcessorProvider$RoutePageSymbolProcessor.parse(RoutePageSymbolProcessorProvider.kt:157)
	at cn.jailedbird.arouter.ksp.compiler.RoutePageSymbolProcessorProvider$RoutePageSymbolProcessor.process(RoutePageSymbolProcessorProvider.kt:85)
```

官方 [issue1329](https://github.com/google/ksp/issues/1329
https://github.com/google/ksp/issues/1330) [issue1330](https://github.com/google/ksp/issues/1330) 提出和修复此问题；

ksp在[1.8.20-1.0.10](https://github.com/google/ksp/releases/tag/1.8.20-1.0.10) 正式修复此问题，这也是本项目需要 *ksp1.8.20-1.0.10+* 的原因，kotlin1.8.20最新的ksp版本是ksp1.8.20-1.0.11； 所以优先使用这个版本；



2、 KSP无法解析注解中的传递的class参数

问题描述：访问interceptors字段会抛出KSTypeNotPresentException

复现记录：6c14c1a4ce38323c8f0af381169dd529b77aefa0

修复记录：7e9f48e548f3887549fef6e573be228488db99dc

```
public @interface RouterPage {
    Class[] interceptors() default {};
}

val ins = page.interceptors.toList()

com.google.devtools.ksp.KSTypesNotPresentException: com.google.devtools.ksp.KSTypeNotPresentException: java.lang.ClassNotFoundException: com.sankuai.waimai.router.demo.fragment2fragment.DemoFragmentInterceptor

```

此时应该使用bennyhuo大佬[issue694](https://github.com/google/ksp/pull/694)的提出的解决方案：compile classpath找不到的时候 应该能获取对应的KSType 从而拿到对应上下文

> This is similar to the MirroredTypeException/MirroredTypesException in APT.
>
> When accessing annotation member with a type of KClass or Array of KClass, ClassNotFoundException will be thrown if the types are not present in the compiler classpath. We should provide a way to let the caller know the KsTypes of the KClass members.



社区中同样的一个问题[issues/1093](issues/1093)， 对应的解决方案也是[类似](https://github.com/catchpig/kmvvm/blob/ksp/compiler/src/main/java/com/catchpig/ksp/compiler/generator/ServiceApiGenerator.kt)



3、 关于第二点的坑

如果Class中的类，是基础类型，比如Object，String，Integer这样的，那么这个时候不会抛出KSTypesNotPresentException异常；此时如果你完全按照异常处理，那么你是无法获取对应的类型的；



我实验出的具体的规则：

- Class数组中，如果存在任何一个自定义的类，那么一定会KSTypesNotPresentException
- 当且全为系统类型的时候，那么不会抛出异常，可以解析对应Class



复现记录：6c14c1a4ce38323c8f0af381169dd529b77aefa0

修复记录：7e9f48e548f3887549fef6e573be228488db99dc



测试用例：这里例子中，interfaces = Object.class可以被正常解析出Class类

```
    @RouterService(interfaces = Object.class, key = "/service/test_annotation_1")
    public static class TestPathService1 {

    }

    @RouterService(interfaces = Object.class, key = "/service/test_annotation_2")
    public static class TestPathService2 {

    }
```



正确代码：同时兼顾异常分支和正常分支；

```
    fun parseAnnotationClassParameter(block: () -> List<KClass<*>>): List<String> {
        return try { // KSTypesNotPresentException will be thrown
            block.invoke().mapNotNull { it.qualifiedName }
        } catch (e: KSTypesNotPresentException) {
            val res = mutableListOf<String>()
            val ksTypes = e.ksTypes
            for (ksType in ksTypes) {
                val declaration = ksType.declaration
                if (declaration is KSClassDeclaration) {
                    declaration.qualifiedName?.asString()?.let {
                        res.add(it)
                    }
                }
            }
            res
        }
    }
```

