package run.mic.bot.util

import com.google.gson.Gson
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import run.mic.bot.Bot

object Lagrange {

    fun getGroupMessage(groupID: Long, messageID: String, count: Int = 1): String?{
        runBlocking {
            val result = Bot.ONEBOT.apiClient.post("${Bot.ONEBOT.apiHost}/get_group_msg_history") {
                val body = buildJsonObject {
                    put("group_id", groupID)
                    put("message_id", messageID)
                    put("count", 1)
                }.toString()

                headers.append("Content-Type", "application/json")
                setBody(body)
            }
            if (result.status != HttpStatusCode.OK) {
                return@runBlocking null
            }
            return@runBlocking result.bodyAsText()
        }
        return null
    }

    fun parseRawMessage(jsonString: String): String {
        val gson = Gson()
        return gson.fromJson(jsonString, ApiResponse::class.java)
            ?.data?.messages?.firstOrNull()?.rawMessage
            ?: ""
    }

}