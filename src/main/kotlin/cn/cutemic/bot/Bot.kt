package cn.cutemic.bot

import cn.cutemic.bot.database.BotService
import cn.cutemic.bot.database.GroupService
import cn.cutemic.bot.database.MessageService
import cn.cutemic.bot.event.EventRegistry.initAllEvents
import cn.cutemic.bot.manager.ModuleManager
import cn.cutemic.bot.manager.TaskManager
import cn.cutemic.bot.model.GroupExposed
import cn.cutemic.bot.util.LegacyDatabaseMove
import cn.cutemic.bot.util.scope.KernelScope
import cn.cutemic.bot.util.scope.runSynchronized
import com.hankcs.hanlp.restful.HanLPClient
import com.huaban.analysis.jieba.WordDictionary
import com.huaban.analysis.jieba.viterbi.FinalSeg
import com.mongodb.ConnectionString
import com.mongodb.MongoClientSettings
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
import love.forte.simbot.component.onebot.v11.core.useOneBot11
import love.forte.simbot.core.application.SimpleApplication
import love.forte.simbot.core.application.launchSimpleApplication
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.apache.logging.log4j.core.config.Configurator
import org.bson.codecs.configuration.CodecRegistries
import org.bson.codecs.pojo.PojoCodecProvider
import org.koin.java.KoinJavaComponent.inject

class Bot {
    private val TRANSFOR_LEGACY_DATABASE = false // 是否迁移旧版数据库(或Pallas-Bot的数据库)

    private lateinit var app: SimpleApplication
    private val groupService by inject<GroupService>(GroupService::class.java)
    private val botService by inject<BotService>(BotService::class.java)
    private val messageService by inject<MessageService>(MessageService::class.java)
    private val groupCache = mutableListOf<Long>()
    private val codecRegistry = CodecRegistries.fromRegistries(
        MongoClientSettings.getDefaultCodecRegistry(),
        CodecRegistries.fromProviders(
            PojoCodecProvider.builder()
                .register("cn.cutemic.bot.model")
                .automatic(true)
                .build()
        )
    )

    init {
        LOGGER.info("System init...")
        KoinBootstrap().start()
        KernelScope.launch {
            app = launchSimpleApplication {
                useOneBot11()
                LOGGER.info("OneBot loading...")
            }
            app.configure()
            app.join()
        }.runSynchronized {
            loadTFIDF()
            TaskManager.loadTask()
        }
    }

    private suspend fun Application.configure() {
        runCatching {
            val botManager = botManagers.firstOneBotBotManager()
            ONEBOT = botManager.register(
                OneBotBotConfiguration().apply {
                    botUniqueId = "2415838976"
                    apiServerHost = Url("http://127.0.0.1:7999")
                    eventServerHost = Url("ws://127.0.0.1:8080/onebot/v11/ws/")
                }
            )
            ONEBOT.start()
            tryInsectGroupAndBotData()
            if (TRANSFOR_LEGACY_DATABASE) {
                val settings = MongoClientSettings.builder()
                    .applyConnectionString(ConnectionString("mongodb://localhost"))
                    .codecRegistry(codecRegistry)
                    .build()
                MongoClient.create(settings).getDatabase("JuJiuBot").let {
                    LOGGER.info("Connect MongoDB success.")
                    LegacyDatabaseMove.transform(it)
                }
            }
            ModuleManager.perloadModule()
            initAllEvents()
        }.onFailure {
            LOGGER.fatal("An error occurred while loading the system: ${it.message}")
            it.printStackTrace()
        }
    }

    private suspend fun tryInsectGroupAndBotData() {
        LOGGER.info("Insect data...")
        val botId = ONEBOT.userId.toLong()
        if (botService.read(ONEBOT.userId.toLong()) == null) {
            botService.add(botId)
            LOGGER.info("Find new bot, added bot($botId) in database.")
        }

        ONEBOT.groupRelation.groups.toList().forEach {
            val groupID = it.id.toLong()
            groupCache.add(it.id.toLong())
            if (groupService.read(groupID) == null) {
                groupService.add(GroupExposed(null, groupID, 0.0, 0.0, null, null))
                LOGGER.info("Find new group, added group($groupID) in database.")
            }
        }
    }

    private fun loadTFIDF() {
        LOGGER.info("TFIDF loading...")
        WordDictionary.getInstance().loadDict()
        FinalSeg.getInstance()
        TFIDF.init()
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