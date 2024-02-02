package cn.jailedbird.arouter.ksp.compiler.utils

/**
 * Some consts used in processors
 *
 * @author Alex [Contact me.](mailto:zhilong.liu@aliyun.com)
 * @version 1.0
 * @since 16/8/24 20:18
 */
object Consts {
    const val NO_MODULE_NAME_TIPS_KSP = "These no module name, at 'build.gradle', like :\n" +
            "ksp {\n" +
            "    arg(\"AROUTER_MODULE_NAME\", project.getName()) {\n" +
            "}\n"
    const val KEY_MODULE_HASH_NAME = "hash"
}