package run.mic.bot.model.legacy.context.fast

import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable
import run.mic.bot.model.legacy.context.LegacyBan

class FastLegacyContext(
    @JvmField val id: String?,
    @JvmField @NotNull @Nullable val keywords: String,
    @JvmField @NotNull @Nullable val time: Long,
    @JvmField @NotNull @Nullable var count: Int,
    @JvmField @NotNull @Nullable var answers: List<FastLegacyAnswers>,
    @JvmField val clearTime: Long?,
    @JvmField var ban: List<LegacyBan>?
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FastLegacyContext) return false

        return id == other.id &&
                keywords == other.keywords &&
                time == other.time &&
                count == other.count &&
                answers == other.answers &&
                clearTime == other.clearTime &&
                ban == other.ban
    }

    override fun hashCode(): Int {
        return arrayOf(id, keywords, time, count, answers, clearTime, ban)
            .contentHashCode()
    }

    override fun toString(): String {
        return "FastLegacyContext(" +
                "id=$id, " +
                "keywords=$keywords, " +
                "time=$time, " +
                "count=$count, " +
                "answers=$answers, " +
                "clearTime=$clearTime, " +
                "ban=$ban, " +
                ")"
    }
}