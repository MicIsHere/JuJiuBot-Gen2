package cn.cutemic.bot

import cn.cutemic.bot.database.BotService
import cn.cutemic.bot.database.GroupService
import cn.cutemic.bot.event.EventRegistry.initAllEvents
import cn.cutemic.bot.manager.ModuleManager
import cn.cutemic.bot.manager.TaskManager
import cn.cutemic.bot.model.GroupExposed
import cn.cutemic.bot.module.impl.Chat.event
import cn.cutemic.bot.util.KernelScope
import cn.cutemic.bot.util.LegacyDatabaseMove
import cn.cutemic.bot.util.runSynchronized
import com.hankcs.hanlp.restful.HanLPClient
import com.huaban.analysis.jieba.WordDictionary
import com.huaban.analysis.jieba.viterbi.FinalSeg
import com.mongodb.kotlin.client.coroutine.MongoClient
import com.qianxinyao.analysis.jieba.keyword.TFIDFAnalyzer
import io.ktor.http.*
import kotlinx.coroutines.launch
import love.forte.simbot.application.Application
import love.forte.simbot.common.collectable.toList
import love.forte.simbot.common.id.toLong
import love.forte.simbot.component.onebot.v11.core.bot.OneBotBot
import love.forte.simbot.component.onebot.v11.core.bot.OneBotBotConfiguration
import love.forte.simbot.component.onebot.v11.core.bot.firstOneBotBotManager
import love.forte.simbot.component.onebot.v11.core.event.message.OneBotNormalGroupMessageEvent
import love.forte.simbot.component.onebot.v11.core.useOneBot11
import love.forte.simbot.core.application.SimpleApplication
import love.forte.simbot.core.application.launchSimpleApplication
import love.forte.simbot.event.EventResult
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.apache.logging.log4j.core.config.Configurator
import org.koin.java.KoinJavaComponent.inject

class Bot {
    private val USE_LEGACY_DATABASE = false // 是否迁移旧版数据库(或Pallas-Bot的数据库)

    private lateinit var app: SimpleApplication
    private val groupService by inject<GroupService>(GroupService::class.java)
    private val botService by inject<BotService>(BotService::class.java)
    private val groupCache = mutableListOf<Long>()

    init {
        LOGGER.info("System init...")

        KoinBootstrap().let {
            LOGGER.info("Koin loading...")
        }

        KernelScope.launch {
            LOGGER.info("OneBot loading...")
            app = launchSimpleApplication {
                useOneBot11()
            }
            app.configure()
            app.join()
        }.runSynchronized {
            LOGGER.info("TFIDF loading...")
            WordDictionary.getInstance().loadDict()
            FinalSeg.getInstance()
            TFIDF.init()
        }

        TaskManager.let {
            LOGGER.info("Task loading...")
        }

        LOGGER.info("System is ok.")
    }

    private suspend fun Application.configure() {
        runCatching {
            val botManager = botManagers.firstOneBotBotManager()
            ONEBOT = botManager.register(
                OneBotBotConfiguration().apply {
                    // 这几个是必选属性
                    // 在OneBot组件中用于区分不同Bot的唯一ID， 建议可以直接使用QQ号。
                    botUniqueId = "2415838976"
                    apiServerHost = Url("http://127.0.0.1:7999")
                    eventServerHost = Url("ws://127.0.0.1:8080/onebot/v11/ws/")
                }
            )

            LOGGER.info("OneBot is ok.")
            ONEBOT.start()

            insectData()
            LOGGER.info("Insect data...")

            if (USE_LEGACY_DATABASE) {
                MongoClient.create("mongodb://localhost").getDatabase("JuJiuBot").let {
                    LOGGER.info("Connect MongoDB success.")
                    LegacyDatabaseMove.transform(it)
                }
            }

            ModuleManager.perLoadModule()
            this.initAllEvents().let {
                LOGGER.info("Init all module(s) event...")
            }
            groupCheck()

            LOGGER.info("Configured.")
        }.onFailure {
            LOGGER.fatal("An error occurred while loading the system: ${it.message}")
            it.printStackTrace()
        }
    }

    private suspend fun insectData(){
        val botId = ONEBOT.userId.toLong()
        if (botService.read(ONEBOT.userId.toLong()) == null) {
            botService.add(botId)
            LOGGER.info("Find new bot, added bot($botId) in database.")
        }

        ONEBOT.groupRelation.groups.toList().forEach {
            val id = it.id.toLong()
            groupCache.add(it.id.toLong())
            if (groupService.read(id) == null) {
                groupService.add(GroupExposed(null, id, 0.0,0.0,null, null))
                LOGGER.info("Find new group, added group($id) in database.")
            }
        }
    }

    private fun groupCheck(){
        event { // 防止运行中加入新群但没有GroupID
            on<OneBotNormalGroupMessageEvent> {
                if (!groupCache.contains(groupId.toLong())) {
                    groupCache.add(groupId.toLong())
                    groupService.add(GroupExposed(null, groupId.toLong(), 0.0,0.0,null, null))
                    LOGGER.info("Find new group on runtime, added group($id) in database.")
                }
                return@on EventResult.empty()
            }
        }
    }

    companion object {
        private val LOG_LEVEL: Level = Level.ALL

        val LOGGER: Logger = LogManager.getLogger("JuJiuBot").let {
            Configurator.setLevel(it.name, LOG_LEVEL)
            return@let it
        }
        val TFIDF = TFIDFAnalyzer()
        var HAN_LP: HanLPClient = HanLPClient("https://www.hanlp.com/api", "")
        lateinit var ONEBOT: OneBotBot
    }
}