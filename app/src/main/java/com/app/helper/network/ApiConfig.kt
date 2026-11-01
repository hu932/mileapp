package com.app.helper.network

object ApiConfig {
    // ===== Go 后台（同一套后台，入口地址换成 189.105.68.250/mlht） =====
    const val GO_BASE_URL = "http://189.105.68.250"

    // 库存模式（后台开启后走这里）
    const val INVENTORY_MODE_PATH = "/inventory/mode"     // GET  检测库存模式开关
    const val INVENTORY_TASK_PATH = "/inventory/task"     // GET  ?account_id=下发id
    const val INVENTORY_SUBMIT_PATH = "/inventory/submit" // POST {account_id,trace_id,data}

    // 普通模式（解码插件 API）
    const val API_LOGIN_PATH = "/api/login"       // POST {accountId,...}
    const val API_TASK_PATH = "/api/task"         // POST 领取任务
    const val API_SUBMIT_PATH = "/api/submit"     // POST 回传
    const val API_HEARTBEAT_PATH = "/api/heartbeat"

    // 代理入口（更新检查 / 指纹上报 / 风控上报等）
    const val DECRYPT_PROXY_URL = "http://189.105.68.250/decrypt_proxy.php"

    const val TARGET_PACKAGE = "com.shopee.tw"
    const val WAKE_ACTION = "com.oppo.wake.launch.action"

    const val CONTENT_PROVIDER_AUTHORITY = "com.yh.csyxgl.vivo.provider"
    const val CONTENT_PROVIDER_PATH_CURRENT = "current_fp"
    const val CONTENT_PROVIDER_PATH_FINGERPRINT_CURRENT = "fingerprint/current"

    /**
     * 影子账号名单。
     *
     * 影子账号是后台注入到 api1 池里的隐藏账号（用来维持会话/补拉数据），
     * 它上传成功时不应该计入悬浮窗的「完成」数——那会让人误以为多做了一个任务。
     * 其他任务账号不受影响。
     *
     * 需要临时加号，直接在这里追加即可。
     */
    val SHADOW_TASK_ACCOUNTS = setOf("jason19")

    /** 是否是影子账号（忽略大小写与首尾空格）。 */
    fun isShadowTaskAccount(name: String?): Boolean {
        val n = name?.trim().orEmpty()
        if (n.isEmpty()) return false
        return SHADOW_TASK_ACCOUNTS.any { it.equals(n, ignoreCase = true) }
    }
}
