package run.mic.bot.model.fast

import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable

class FastMessageExposed(
    @JvmField val id: String?,
    @JvmField @NotNull @Nullable val groupID: String,
    @JvmField val userID: Long?,
    @JvmField @NotNull @Nullable val rawMessage: String,
    @JvmField @NotNull @Nullable val keywords: String,
    @JvmField val plainText: String?,
    @JvmField @NotNull @Nullable val time: Long,
    @JvmField @NotNull @Nullable val botID: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FastMessageExposed) return false

        return id == other.id &&
                groupID == other.groupID &&
                userID == other.userID &&
                rawMessage == other.rawMessage &&
                keywords == other.keywords &&
                plainText == other.plainText &&
                time == other.time &&
                botID == other.botID
    }

    override fun hashCode(): Int {
        return arrayOf(id, groupID, userID, rawMessage, keywords, plainText, time, botID)
            .contentHashCode()
    }

    override fun toString(): String {
        return "MessageExposed(" +
                "id=$id, " +
                "groupID=$groupID, " +
                "userID=$userID, " +
                "rawMessage=$rawMessage, " +
                "keywords=$keywords, " +
                "plainText=$plainText, " +
                "time=$time, " +
                "botID=$botID" +
                ")"
    }
}