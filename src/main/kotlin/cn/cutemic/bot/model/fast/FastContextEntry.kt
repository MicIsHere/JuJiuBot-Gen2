package cn.cutemic.bot.model.fast

import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable

class FastContextEntry(
    @JvmField val id: String?,
    @JvmField @NotNull @Nullable val keywords: String,
    @JvmField @NotNull @Nullable val keywordsWeight: String,
    @JvmField @NotNull @Nullable var count: Int,
    @JvmField @NotNull @Nullable var lastUpdated: Long,
    @JvmField val legacyID: String?
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FastContextEntry) return false

        return id == other.id &&
                keywords == other.keywords &&
                keywordsWeight == other.keywordsWeight &&
                count == other.count &&
                lastUpdated == other.lastUpdated &&
                legacyID == other.legacyID
    }

    override fun hashCode(): Int {
        return arrayOf(id, keywords, keywordsWeight, count, lastUpdated, legacyID)
            .contentHashCode()
    }

    override fun toString(): String {
        return "FastContextEntry(" +
                "id=$id, " +
                "keywords=$keywords, " +
                "keywordsWeight=$keywordsWeight, " +
                "count=$count, " +
                "lastUpdated=$lastUpdated, " +
                "legacyID=$legacyID, " +
                ")"
    }
}