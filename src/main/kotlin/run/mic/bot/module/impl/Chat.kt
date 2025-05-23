package run.mic.bot.module.impl

import kotlinx.coroutines.runBlocking
import love.forte.simbot.common.id.toLong
import love.forte.simbot.component.onebot.common.annotations.InternalOneBotAPI
import love.forte.simbot.component.onebot.v11.core.event.message.OneBotNormalGroupMessageEvent
import love.forte.simbot.component.onebot.v11.core.utils.sendGroupTextMsgApi
import love.forte.simbot.component.onebot.v11.message.segment.OneBotReply
import love.forte.simbot.component.onebot.v11.message.segment.OneBotText
import love.forte.simbot.component.onebot.v11.message.segment.oneBotSegmentOrNull
import love.forte.simbot.event.EventResult
import love.forte.simbot.message.Messages
import org.koin.java.KoinJavaComponent.inject
import run.mic.bot.Bot
import run.mic.bot.Trace
import run.mic.bot.database.*
import run.mic.bot.model.BlockExposed
import run.mic.bot.model.MessageExposed
import run.mic.bot.model.context.AnswerEntry
import run.mic.bot.model.context.ContextEntry
import run.mic.bot.module.BotModule
import run.mic.bot.util.IgnoreCommand
import run.mic.bot.util.Lagrange
import run.mic.bot.util.Task
import java.util.concurrent.ThreadLocalRandom
import java.util.regex.Pattern
import kotlin.math.*

@OptIn(InternalOneBotAPI::class)
object Chat : BotModule("聊天", "与牛牛聊天") {

    /* 运行时变量 */
    private val random = ThreadLocalRandom.current() // 脱离线程随机
    private val messageID = mutableListOf<Pair<String, String>>() // 维护一个消息列表
    private var answerMap = mutableListOf<Pair<String, AnswerEntry>>()

    /* 数据库相关 */
    private val groupService by inject<GroupService>(GroupService::class.java)
    private val botService by inject<BotService>(BotService::class.java)
    private val contextService by inject<ContextService>(ContextService::class.java)
    private val answerService by inject<AnswerService>(AnswerService::class.java)
    private val messageService by inject<MessageService>(MessageService::class.java)
    private val blockService by inject<BlockService>(BlockService::class.java)

    /* 运行参数 */
    private const val KEYWORD_SIZE: Int = 2 // 学习关键词数量
    private const val CROSS_GROUP_THRESHOLD: Int = 2 // N 个群有相同的回复，就跨群作为全局回复
    private const val BASE_REPLY_PROB: Double = 0.4 // 基础回复概率
    private const val TOPICS_IMPORTANCE: Double = 0.5 // 话题重要性
    private const val SPLIT_PROBABILITY: Double = 0.3 // 回复分句概率
    private const val IGNORE_LEARN: Double = 0.05 // 跳过学习概率

    init {
        event {
            on<OneBotNormalGroupMessageEvent> {
                val message = messageContent.plainText ?: ""
                if (IgnoreCommand.equals(message)) {
                    return@on EventResult.invalid()
                }
                val bot = botService.read(bot.userId.toLong()) ?: throw NullPointerException("无法获取当前Bot的ID")
                val groupExposed = groupService.read(groupId.toLong()) ?: throw NullPointerException("无法获取当前GroupID")

                if (isBanCommand(messageContent.messages)) {
                    val banMessageID = messageContent.messages.single { it.oneBotSegmentOrNull<OneBotReply>() != null }.oneBotSegmentOrNull<OneBotReply>() ?: return@on EventResult.empty()
                    Trace.info("获取消息ID: ${banMessageID.id}")
                    val banMessageResult = Lagrange.getGroupMessage(groupId.toLong(), banMessageID.id.toString(), 1) ?: return@on EventResult.empty()
                    val targetRawMessage = Pattern.compile("(\\[CQ:.+?)(,url=[^]]+)?(])")
                        .matcher(Lagrange.parseRawMessage(banMessageResult)).replaceAll("$1$3")
                        .toString()
                    Trace.info("返回数据($targetRawMessage)： $banMessageResult")
                    val messageID = messageService.readByGroupID(groupExposed.id!!, targetRawMessage).firstOrNull()?.id ?: return@on EventResult.empty()
                    val answer = answerService.get(groupExposed.id, messageID)
                    Trace.info("准备提交封禁请求: ${groupExposed.id}, $messageID, ${answer != null}")
                    reply("这对角可能会不小心撞倒些家具，我会尽量小心。")
                    ban(
                        answer,
                        bot,
                        userId.value
                    )
                    return@on EventResult.empty()
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

                if (random.nextDouble() >= calculateReplyProbability(groupService.read(groupExposed.id)?.activity ?: 0.0)) { // 计算回复概率
                    return@on EventResult.empty()
                }

                reply(chatData).let { answerEntry ->
                    answerEntry ?: return@on EventResult.empty()
                    val replyMessage = messageService.read(answerEntry.message)?.rawMessage ?: return@on EventResult.empty()
                    answerMap.add(answerEntry.group!! to answerEntry)
                    if (message.contains(",") && random.nextDouble() < SPLIT_PROBABILITY) {// 概率分割逗号并分句回复
                        Trace.info("本次触发分句回复")
                        replyMessage
                            .flatMap { replyMessage.split(",") }
                            .forEach {
                                Bot.ONEBOT.executeData(sendGroupTextMsgApi(groupId, it))
                            }
                        return@on EventResult.empty()
                    }
                    Trace.info("发送回复消息: $replyMessage")
                    Bot.ONEBOT.executeData(sendGroupTextMsgApi(groupId, replyMessage))
                }
                EventResult.empty()
            }
        }
    }

    private suspend fun learn(data: MessageExposed) {
        if (messageID.none { it.second == data.groupID }) {
            messageService.add(data).let {
                messageID.add(Pair(it, data.groupID))
            }
        }

        val lastMessageID = messageID
            .lastOrNull { it.second == data.groupID }
            ?.first
            ?: throw NullPointerException("获取最新消息ID失败")

        val lastMessage =
            messageService.read(lastMessageID) ?: throw NullPointerException("获取最新消息失败")
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
            messageService.readListByGroupID(data.groupID)
                .asReversed()
                .take(3)
                .firstOrNull { msg ->
                    // 由于兼容旧版数据库的原因，msg.userID 可能为空。
                    // requireNotNull(msg.userID) { "Invalid user ID in message" }
                    msg.plainText != lastMessage.plainText // 复读检查
                }?.let { foundMsg ->
                    val message = foundMsg.plainText ?: foundMsg.rawMessage
                    val foundMessageKeyword = analyzeText(message)
                    val contextID = insectContext(foundMessageKeyword.first, foundMessageKeyword.second)
                    answerService.getAnswerByContextId(contextID).singleOrNull { it.message == lastMessage.id }?.let {
                        answerService.updateCount(it.id!!, it.count++)
                        return
                    }

                    val allAnswer = answerService.getAnswerByContextId(contextID)
                        .filter { it.group != data.groupID }
                        .filter { it.message == lastMessage.id }
                    if (allAnswer.size >= CROSS_GROUP_THRESHOLD) { // 添加全局 Answer
                        allAnswer.forEach { answerService.delete(it.id!!) }
                        answerService.add(
                            AnswerEntry(
                                null,
                                null,
                                1,
                                contextID,
                                lastMessage.id!!,
                                System.currentTimeMillis()
                            )
                        )
                        return
                    }

                    answerService.add(
                        AnswerEntry(
                            null,
                            data.groupID,
                            1,
                            contextID,
                            lastMessage.id!!,
                            System.currentTimeMillis()
                        )
                    )
                }

            val message1 = lastMessage.plainText ?: lastMessage.rawMessage
            val analysis1 = analyzeText(message1)
            insectContext(analysis1.first, analysis1.second).let { context ->
                answerService.getAnswerByContextId(context).singleOrNull { it.message == lastMessage.id }?.let {
                    answerService.updateCount(it.id!!, it.count++)
                    return
                }

                val allAnswer = answerService.getAnswerByContextId(context)
                    .filter { it.group != data.groupID }
                    .filter { it.message == lastMessage.id }
                if (allAnswer.size >= CROSS_GROUP_THRESHOLD) { // 添加全局 Answer
                    allAnswer.forEach { answerService.delete(it.id!!) }
                    answerService.add(
                        AnswerEntry(
                            null,
                            null,
                            1,
                            context,
                            lastMessage.id!!,
                            System.currentTimeMillis()
                        )
                    )
                    return
                }

                answerService.add(
                    AnswerEntry(
                        null,
                        data.groupID,
                        1,
                        context,
                        lastMessage.id!!,
                        System.currentTimeMillis()
                    )
                )
            }
        }
    }

    /**
     * 分析文本关键字
     *
     * 将会以TF-IDF，HanLP，原文本的顺序尝试分析文本。
     *
     * First为关键字，Second为权重。
     */
    private fun analyzeText(text: String): Pair<String, String> {
        val keywordList = Bot.TFIDF.analyze(text, KEYWORD_SIZE)
        val result = StringBuilder()
        val weightResult = StringBuilder()
        var count = 1 // size 跟 index 是不一样的

        if (keywordList.isEmpty()) {
            Trace.warn("TD-IDF 分词失败, 尝试使用HanLp")
            runCatching {
                val list = Bot.HAN_LP.keyphraseExtraction(text, KEYWORD_SIZE)
                // HanLP 也可能分析不出数据
                if (list.isEmpty()) {
                    throw NullPointerException("HanLp分词失败")
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
                Trace.warn("HanLp分词失败, 使用原文本")
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
     * 成功获取到回答后将返回Answer对象
     */
    private suspend fun reply(data: MessageExposed): AnswerEntry? {
        Trace.info("尝试回复消息：${data.rawMessage}")
        return selectWeightedAnswer(data)
    }

    /**
     * 向数据库置入上下文
     */
    private suspend fun insectContext(keyword: String, weight: String): String {
        var contextID = contextService.getId(keyword)
        if (contextID == null) {
            contextID = contextService.add(
                ContextEntry(
                    null,
                    keywords = keyword,
                    keywordsWeight = weight,
                    count = 1,
                    lastUpdated = System.currentTimeMillis(),
                    null
                )
            )
            return contextID
        }

        val count = contextService.get(contextID)!!.count
        contextService.update(
            ContextEntry(
                contextID,
                keywords = keyword,
                keywordsWeight = weight,
                count = count + 1,
                lastUpdated = System.currentTimeMillis(),
                null
            )
        )
        Trace.info("上下文权重已更新($contextID): $count -> ${count + 1}")
        return contextID
    }

    /**
     * 每半小时计算一次群聊活跃度
     */
    @Task(60 * 30)
    private fun calcGroupActivity() {
        runBlocking {
            Trace.info("开始计算群聊活跃度")
            groupService.readAll().forEach { it ->
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

    private fun ban(answerEntry: AnswerEntry?, botId: String, userID: Long?){
        if (answerEntry == null) {
            Trace.warn("提交的Answer为空")
            return
        }

        Trace.info("封禁消息: ${answerEntry.message}, 上下文: ${answerEntry.context}")
        runBlocking {
            blockService.add(BlockExposed(
                null,
                botId,
                answerEntry.group!!,
                userID,
                answerEntry.id!!,
                "Ban request made by $userID from group ${answerEntry.group}",
                System.currentTimeMillis()
            ))
        }
    }

    private fun Double.roundTo(decimals: Int): Double {
        val factor = 10.0.pow(decimals)
        return (this * factor).roundToInt() / factor
    }

    private fun isBanCommand(messages: Messages): Boolean{
        return (messages.any { it.oneBotSegmentOrNull<OneBotReply>() != null }
                && messages.any { it.oneBotSegmentOrNull<OneBotText>()?.data?.text?.contains("不可以") == true })
    }
}