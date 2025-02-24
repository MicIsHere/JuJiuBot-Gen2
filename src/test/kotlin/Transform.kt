import cn.cutemic.bot.Bot
import com.mongodb.client.model.*
import com.mongodb.kotlin.client.coroutine.MongoClient
import com.mongodb.kotlin.client.coroutine.MongoCollection
import kotlinx.coroutines.flow.toList
import org.bson.Document

suspend fun main(){
    val client = MongoClient.create("mongodb://localhost").getDatabase("JuJiuBot")
    val collection: MongoCollection<Document> = client.getCollection("message")
    // bot_id
    // group_id
    // is_plain_text
    // user_id
    // time
    // 以上均为需要转成String的列，因为不知道为什么他会混着类型存数据。。。
    val targetName = "time"

    println("Processing...")
    // 使用 updateMany 来批量更新数据类型
    val cursor = collection.find().toList().iterator()
    val bulkOperations = mutableListOf<WriteModel<Document>>()

    while (cursor.hasNext()) {
        val document = cursor.next()
        val groupId = document[targetName]

        if (groupId !is String) {
            val updatedGroupId = groupId.toString()

            Bot.LOGGER.info("Changed.")

            // 构建批量操作
            bulkOperations.add(
                UpdateOneModel(
                    Filters.eq("_id", document.getObjectId("_id")),
                    Updates.set(targetName, updatedGroupId)
                )
            )
        }
    }

    println("${bulkOperations.size} need changed, posting request...")

    if (bulkOperations.isNotEmpty()) {
        collection.bulkWrite(bulkOperations)
    }

    println("Done!")

}