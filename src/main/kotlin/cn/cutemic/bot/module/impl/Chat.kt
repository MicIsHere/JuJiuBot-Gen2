package cn.cutemic.bot.module.impl

import cn.cutemic.bot.Bot
import cn.cutemic.bot.database.*
import cn.cutemic.bot.model.IgnoreCommand
import cn.cutemic.bot.model.MessageExposed
import cn.cutemic.bot.model.context.AnswerEntry
import cn.cutemic.bot.model.context.ContextEntry
import cn.cutemic.bot.module.BotModule
import cn.cutemic.bot.util.Task
import kotlinx.coroutines.runBlocking
import love.forte.simbot.common.id.toLong
import love.forte.simbot.component.onebot.common.annotations.InternalOneBotAPI
import love.forte.simbot.component.onebot.v11.core.event.message.OneBotNormalGroupMessageEvent
import love.forte.simbot.component.onebot.v11.core.utils.sendGroupTextMsgApi
import love.forte.simbot.event.EventResult
import org.koin.java.KoinJavaComponent.inject
import java.util.concurrent.ThreadLocalRandom
import kotlin.math.*

@OptIn(InternalOneBotAPI::class)
object Chat: BotModule("聊天","与牛牛聊天") {

    /* 运行时变量 */
    private val random = ThreadLocalRandom.current() // 脱离线程随机
    private val messageID = mutableListOf<Pair<String, String>>() // 维护一个消息列表

    /* 数据库相关变量 */
    private val groupService by inject<GroupService>(GroupService::class.java)
    private val botService by inject<BotService>(BotService::class.java)
    private val contextService by inject<ContextService>(ContextService::class.java)
    private val answerService by inject<AnswerService>(AnswerService::class.java)
    private val messageService by inject<MessageService>(MessageService::class.java)

    /* 运行参数 */
    private const val KEYWORD_SIZE = 2 // 学习关键词数量
    private const val BASE_REPLY_PROB: Double = 0.4 // 基础回复概率
    private const val TOPICS_IMPORTANCE: Double = 0.5 // 话题重要性
    private const val SPLIT_PROBABILITY: Double = 0.3 // 回复分句概率
    private const val IGNORE_LEARN: Double = 0.05 // 跳过学习概率

    init {
        event {
            on<OneBotNormalGroupMessageEvent> {
                val message = messageContent.plainText ?: ""
                val bot = botService.read(bot.userId.toLong()) ?: throw NullPointerException("Cannot get bot-id in database.")
                val groupExposed = groupService.read(groupId.toLong()) ?: throw NullPointerException("Cannot get group-id in database.")

                if (rawMessage.startsWith("[CQ:")) {
                    return@on EventResult.invalid()
                }

                if (IgnoreCommand.equals(message)) {
                    return@on EventResult.invalid()
                }

                val chatData = MessageExposed(
                    null,
                    groupExposed.id!!,
                    userId.value,
                    rawMessage,
                    analyzeText(message).first,
                    message,
                    time.milliseconds,
                    bot
                )

                if (shouldLearn(message)) {
                    learn(chatData)
                }

                if (random.nextDouble() >= calculateReplyProbability(groupService.read(groupExposed.id)?.activity ?: 0.0)) { // 查找回复概率
                    return@on EventResult.empty()
                }

                reply(chatData).let { replyMessage ->
                    if (replyMessage == null) {
                        return@on EventResult.empty()
                    }

                    // 概率分割逗号并分句回复
                    if (replyMessage.contains(",") && random.nextDouble() < SPLIT_PROBABILITY) {
                        Bot.LOGGER.info("Spilt reply.")
                        replyMessage
                            .flatMap { replyMessage.split(",") }
                            .forEach {
                                Bot.ONEBOT.executeData(sendGroupTextMsgApi(groupId, it))
                            }
                        return@on EventResult.empty()
                    }

                    Bot.LOGGER.info("Send reply message: $replyMessage")
                    Bot.ONEBOT.executeData(sendGroupTextMsgApi(groupId, replyMessage))
                }
                EventResult.empty()
            }
        }
    }

    private suspend fun learn(data: MessageExposed){
        // 获取群里的上一条发言 当作现在处理这条消息的(问题)
        if (messageID.isEmpty()) {
            messageService.add(data).let {
                messageID.add(Pair(it, data.groupID))
            }
            return
        }

        val lastMessageID = messageID.last { it.second == data.groupID }.first
        val lastMessage = messageService.read(lastMessageID) ?: throw NullPointerException("Cannot get last message in database.")

        // 添加这次发言的信息数据
        messageService.add(data).let {
            messageID.add(Pair(it, data.groupID))
        }
        // 复读检查
        if (lastMessage.plainText == data.plainText) {
            return
        }
        // 上一条发言用户不是这次数据的用户 将尝试获取前三条数据
        if (lastMessage.userID != data.userID) {
            runCatching {
                messageService.readListByGroupID(data.groupID)
                    .asReversed()
                    .take(3)
                    .firstOrNull { msg ->
                        requireNotNull(msg.userID) { "Invalid user ID in message" }
                        msg.userID == lastMessage.userID && msg.plainText != lastMessage.plainText // 复读检查
                    }?.let { foundMsg ->
                        val foundMessageKeyword = analyzeText(foundMsg.plainText!!)
                        val contextID = insectContext(foundMessageKeyword.first, foundMessageKeyword.second)
                        answerService.getAnswerByContextId(contextID).singleOrNull { it.message == lastMessage.id }?.let {
                            answerService.updateCount(it.id!!, it.count++)
                            return@runCatching
                        }

                        answerService.add(AnswerEntry(
                            null,
                            data.groupID,
                            1,
                            contextID,
                            lastMessage.id!!,
                            System.currentTimeMillis()
                        ))
                    }
            }.onFailure {
                Bot.LOGGER.error("Error processing messages: ${it.message}")
                throw it
            }
        }

        val analysis1 = analyzeText(lastMessage.plainText!!)
        insectContext(analysis1.first, analysis1.second).let { context ->
            answerService.getAnswerByContextId(context).singleOrNull { it.message == lastMessage.id }?.let {
                answerService.updateCount(it.id!!, it.count++)
                return
            }

            answerService.add(AnswerEntry(
                null,
                data.groupID,
                1,
                context,
                lastMessage.id!!,
                System.currentTimeMillis()
            ))
        }
    }

    /**
     * 分析文本关键字
     *
     * 将会以TF-IDF，HanLP，原文本的顺序尝试分析文本。
     *
     * First为关键字，Second为权重。
     */
    private fun analyzeText(text: String): Pair<String, String>{
        val keywordList = Bot.TFIDF.analyze(text, KEYWORD_SIZE)
        val result = StringBuilder()
        val weightResult = StringBuilder()
        var count = 1 // size 跟 index 是不一样的

        if (keywordList.isEmpty()) {
            Bot.LOGGER.warn("TD-IDF Analyze is failed, trying use HanLp API...")
            runCatching {
                val list = Bot.HAN_LP.keyphraseExtraction(text, KEYWORD_SIZE)
                // HanLP 也可能分析不出数据
                if (list.isEmpty()) {
                    throw NullPointerException("HanLp Analyze is failed")
                }
                list.forEach {
                    if (count == list.size) {
                        count = 0
                        result.append(it.key)
                        weightResult.append(it.value)
                        return Pair(result.toString(), weightResult.toString())
                    }
                    count++
                    result.append("${it.key}|")
                    weightResult.append("${it.value}|")
                }
            }.onFailure {
                // HanLP分析失败 使用原文本当作上下文
                Bot.LOGGER.error("HanLp Analyze is failed, use raw-text...")
                result.append(text)
            }
        } else {
            keywordList.forEach {
                if (count == keywordList.size) {
                    count = 0
                    result.append(it.name)
                    weightResult.append(it.tfidfvalue)
                    return Pair(result.toString(), weightResult.toString())
                }
                count++
                result.append("${it.name}|")
                weightResult.append("${it.tfidfvalue}|")
            }
        }
        // 全部分析失败
        return Pair(text, "1.0")
    }

    /**
     * 判断是否需要学习。
     *
     * 将会过滤空消息，长度小于2的消息。
     *
     * 有概率跳过学习。
     */
    private fun shouldLearn(message: String): Boolean {
        return when {
            message.isBlank() -> false
            message.length < 2 -> false
            random.nextDouble() < IGNORE_LEARN -> false // 概率跳过学习
            else -> true
        }
    }

    /**
     * 计算回复概率
     *
     * 由群组活跃度和基础回复概率进行计算
     */
    private fun calculateReplyProbability(groupActivity: Double): Double {
        val adjustedProb = BASE_REPLY_PROB * groupActivity
        return min(max(adjustedProb, 0.1), 0.8)
    }

    /**
     * 输出一条最符合的AnswerEntry
     *
     * 使用传入的消息进行 加权随机选择
     */
    private suspend fun selectWeightedAnswer(data: MessageExposed): AnswerEntry? {
        if (data.plainText == null || data.plainText != "" && data.plainText.length < 2) {
            return null
        }

        val contextId = contextService.getId(data.keywords) ?: return null
        val answerList = answerService.getAnswerByContextId(contextId)

        if (answerList.isEmpty()) {
            return null
        }

        val weights = answerList
            .filter { it.group == null || it.group == data.groupID }
            .map { candidate ->
            val timeDecay = exp(-(System.currentTimeMillis() - candidate.lastUsed) / 1_000_000.0)
            val keywordList = contextService.get(candidate.context)!!.keywords.split("|")
            val keywordWeightList = contextService.get(candidate.context)!!.keywordsWeight.split("|")

            var weightCount = 0
            val topical = keywordList.sumOf {
                keywordWeightList[weightCount]
                weightCount++
            }.let {
                weightCount = 0
                it
            }

            min(candidate.count.toDouble(), 10.0) +
                    topical * TOPICS_IMPORTANCE +
                    timeDecay
        }

        val totalWeight = weights.sum()
        if (totalWeight <= 0) return null

        val rand = random.nextDouble() * totalWeight
        var accum = 0.0

        answerList.forEachIndexed { index, candidate ->
            accum += weights[index]
            if (accum >= rand) return candidate
        }
        return answerList.last()
    }

    /**
     * 尝试回复
     *
     * 成功获取到回答后将返回String
     */
    private suspend fun reply(data: MessageExposed): String?{
        Bot.LOGGER.info("Trying reply...")
        val answer = selectWeightedAnswer(data) ?: return null
        return messageService.read(answer.message)?.rawMessage
    }

    /**
     * 向数据库置入上下文
     */
    private suspend fun insectContext(keyword: String, weight: String): String{
        var contextID = contextService.getId(keyword)
        if (contextID == null) {
            contextID = contextService.add(ContextEntry(
                keywords = keyword,
                keywordsWeight = weight,
                count = 1,
                lastUpdated = System.currentTimeMillis()
            ))
            return contextID
        }
        Bot.LOGGER.info("Update context count.")
        val count = contextService.get(contextID)!!.count++
        contextService.update(contextID, ContextEntry(
            keywords = keyword,
            keywordsWeight = weight,
            count = count,
            lastUpdated = System.currentTimeMillis()
        ))
        return contextID
    }

    /**
     * 每半小时计算一次群聊活跃度
     */
    @Task(60 * 30)
    private fun calcGroupActivity(){
        runBlocking {
            Bot.LOGGER.info("Start calc group activity...")
            groupService.readAll().forEach {
                val messages = messageService.readLastMessages(it.id!!, 50)
                if (messages.size < 2) {
                    groupService.updateActivity(it.id, 0.0)
                    return@forEach
                }

                // 计算时间跨度（秒）
                val timeSpan = (messages.first().time - messages.last().time) / 1000.0
                val msgRate = messages.size / max(timeSpan, 1.0)

                // 计算用户多样性
                val uniqueUsers = messages.map { it.userID }.toSet().size
                val diversityFactor = 1 + 0.05 * uniqueUsers

                // 计算时间衰减（分钟）
                val lastMsgAgeMin = (System.currentTimeMillis() - messages.first().time) / 60000.0
                val timeDecay = 1.0 / (lastMsgAgeMin + 1)
                groupService.updateActivity(it.id, (msgRate * diversityFactor * timeDecay).roundTo(2))
            }
        }
    }

    private fun Double.roundTo(decimals: Int): Double {
        val factor = 10.0.pow(decimals)
        return (this * factor).roundToInt() / factor
    }
}