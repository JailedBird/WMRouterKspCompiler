
## 关键问题记录

1、 使用KSP解析 Java注解中的数组 会出现类型转换错误

```
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

```

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

社区中Issue中有相同的问题和解决方案，在1.8.20-1.0.10 https://github.com/google/ksp/releases/tag/1.8.20-1.0.10 中修复好了
https://github.com/google/ksp/issues/1329
https://github.com/google/ksp/issues/1330

2、 KSP无法解析注解中的传递的class参数

```
public @interface RouterPage {
    String[] path();
    Class[] interceptors() default {};
}

val ins = page.interceptors.toList()

com.google.devtools.ksp.KSTypesNotPresentException: com.google.devtools.ksp.KSTypeNotPresentException: java.lang.ClassNotFoundException: com.sankuai.waimai.router.demo.fragment2fragment.DemoFragmentInterceptor

```

https://github.com/google/ksp/pull/694
应该使用bennyhuo大佬的提出的解决方案：compile classpath找不到的时候 应该能获取对应的KSType 从而拿到对应上下文
This is similar to the MirroredTypeException/MirroredTypesException in APT.

When accessing annotation member with a type of KClass or Array of KClass, ClassNotFoundException will be thrown if the types are not present in the compiler classpath. We should provide a way to let the caller know the KsTypes of the KClass members.

相似问题：https://github.com/google/ksp/issues/1093
对应的解决方案：https://github.com/catchpig/kmvvm/blob/ksp/compiler/src/main/java/com/catchpig/ksp/compiler/generator/ServiceApiGenerator.kt
