package run.mic.bot.util

object CqCode {

    // 获取CQ码类型
    fun getType(raw: String): String? {
        return when (raw) {
            in "[CQ:reply," -> "reply" // 回复
            in "[CQ:image," -> "image" // 图片
            in "[CQ:at," -> "at" // At
            in "[CQ:record," -> "record" // 语音
            in "[CQ:poke," -> "poke" // 戳一戳
            in "[CQ:share," -> "share" // 分享链接
            in "[CQ:redbag," -> "redbag" // 红包
            in "[CQ:video," -> "video" // 短视频
            else -> null
        }
    }

    // 解析CQ码
    fun parse(cqString: String): CQCode? {
        if (!cqString.startsWith("[CQ:") || !cqString.endsWith("]")) return null
        val content = cqString.removeSurrounding("[CQ:", "]")
        val segments = content.split(',')
        if (segments.isEmpty()) return null
        val type = segments.first()
        val params = segments
            .drop(1) // 跳过类型字段
            .associate {
                val (key, value) = it.split('=', limit = 2)
                key to value
            }

        return CQCode(type, params)
    }

    fun createReplyCqCode(){
        // TODO
    }

    data class CQCode(val type: String, val params: Map<String, String>)
}