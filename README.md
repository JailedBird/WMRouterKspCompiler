
## 关键问题记录

1、 使用KSP解析 Java注解中的数组 会出现类型转换错误

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

