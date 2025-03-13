package run.mic.bot

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
import org.bson.codecs.configuration.CodecRegistries
import org.bson.codecs.pojo.PojoCodecProvider
import org.koin.java.KoinJavaComponent.inject
import run.mic.bot.database.BotService
import run.mic.bot.database.GroupService
import run.mic.bot.database.MessageService
import run.mic.bot.event.EventRegistry.initAllEvents
import run.mic.bot.manager.ModuleManager
import run.mic.bot.manager.TaskManager
import run.mic.bot.model.GroupExposed
import run.mic.bot.util.LegacyDatabaseMove
import run.mic.bot.util.scope.KernelScope
import run.mic.bot.util.scope.runSynchronized

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
                .register("run.mic.bot.model")
                .automatic(true)
                .build()
        )
    )

    init {
        Trace.info("System init...")
        KoinBootstrap().start()
        KernelScope.launch {
            app = launchSimpleApplication {
                useOneBot11()
                Trace.info("OneBot loading...")
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
                    Trace.info("Connect MongoDB success.")
                    LegacyDatabaseMove.transform(it)
                }
            }
            ModuleManager.perloadModule()
            initAllEvents()
        }.onFailure {
            Trace.fatal("An error occurred while loading the system: ${it.message}")
            it.printStackTrace()
        }
    }

    private suspend fun tryInsectGroupAndBotData() {
        Trace.info("Insect data...")
        val botId = ONEBOT.userId.toLong()
        if (botService.read(ONEBOT.userId.toLong()) == null) {
            botService.add(botId)
            Trace.info("Find new bot, added bot($botId) in database.")
        }

        ONEBOT.groupRelation.groups.toList().forEach {
            val groupID = it.id.toLong()
            groupCache.add(it.id.toLong())
            if (groupService.read(groupID) == null) {
                groupService.add(GroupExposed(null, groupID, 0.0, 0.0, null, null))
                Trace.info("Find new group, added group($groupID) in database.")
            }
        }
    }

    private fun loadTFIDF() {
        Trace.info("TFIDF loading...")
        WordDictionary.getInstance().loadDict()
        FinalSeg.getInstance()
        TFIDF.init()
    }

    companion object {
        val LOG_LEVEL: Level = Level.ALL
        val TFIDF = TFIDFAnalyzer()
        var HAN_LP: HanLPClient = HanLPClient("https://www.hanlp.com/api", "")
        lateinit var ONEBOT: OneBotBot
        val CRASH = false // 是否在发生错误时崩溃
    }
}