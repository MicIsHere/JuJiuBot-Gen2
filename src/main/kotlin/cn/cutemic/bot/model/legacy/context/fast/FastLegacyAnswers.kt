package cn.cutemic.bot.model.legacy.context.fast

import org.bson.codecs.pojo.annotations.BsonCreator
import org.bson.codecs.pojo.annotations.BsonProperty
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable

@Deprecated("该类仅可用于迁移旧版数据库")
class FastLegacyAnswers @BsonCreator constructor(
    @BsonProperty("keywords") @JvmField @NotNull @Nullable val keywords: String,
    @BsonProperty("group_id") @JvmField @NotNull @Nullable val groupID: Long,
    @BsonProperty("count") @JvmField @NotNull @Nullable val count: Int,
    @BsonProperty("time") @JvmField @NotNull @Nullable val time: Long,
    @BsonProperty("messages") @JvmField @NotNull @Nullable val messages: List<String>
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FastLegacyAnswers) return false

        return keywords == other.keywords &&
                groupID == other.groupID &&
                count == other.count &&
                time == other.time &&
                messages == other.messages
    }

    override fun hashCode(): Int {
        return arrayOf(keywords, groupID, count, time, messages).contentHashCode()
    }

    override fun toString(): String {
        return "FastLegacyAnswers(" +
                "keywords='$keywords', " +
                "groupID=$groupID, " +
                "count=$count, " +
                "time=$time, " +
                "messages=${messages.joinToString()}" +
                ")"
    }
}