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
import run.mic.bot.config.Config
import run.mic.bot.config.data.ConfigBase
import run.mic.bot.config.data.ConfigProtocol
import run.mic.bot.config.data.database.ConfigDatabase
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

class Bot(val config: ConfigBase) {
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
        Trace.info("系统初始化...")
        KoinBootstrap(config.database).start()
        KernelScope.launch {
            app = launchSimpleApplication {
                useOneBot11()
                Trace.info("OneBot 加载...")
            }
            app.configure(config.protocol, config.database)
            app.join()
        }.runSynchronized {
            loadTFIDF()
            TaskManager.loadTask()
        }
    }

    private suspend fun Application.configure(protocol: ConfigProtocol, database: ConfigDatabase) {
        runCatching {
            val botManager = botManagers.firstOneBotBotManager()
            Trace.info("以${protocol.botUniqueId}身份连接到 ${protocol.apiServerHost}")
            ONEBOT = botManager.register(
                OneBotBotConfiguration().apply {
                    botUniqueId = protocol.botUniqueId.toString()
                    apiServerHost = Url(protocol.apiServerHost)
                    eventServerHost = Url(protocol.eventServerHost)
                }
            )
            ONEBOT.start()
            tryInsectGroupAndBotData()
            if (database.mongoDB.transferOldData) {
                Trace.info("开始迁移旧数据...")
                val settings = MongoClientSettings.builder()
                    .applyConnectionString(ConnectionString("mongodb://${database.mongoDB.host}:${database.mongoDB.port}"))
                    .codecRegistry(codecRegistry)
                    .build()
                MongoClient.create(settings).getDatabase(database.mongoDB.databaseName).let {
                    Trace.info("连接 MongoDB 成功.")
                    LegacyDatabaseMove.transform(it)
                }
            }
            ModuleManager.perloadModule()
            initAllEvents()
        }.onFailure {
            Trace.fatal("在系统加载时出现错误: ${it.message}")
            it.printStackTrace()
        }
    }

    private suspend fun tryInsectGroupAndBotData() {
        Trace.info("置入数据...")
        val botId = ONEBOT.userId.toLong()
        if (botService.read(ONEBOT.userId.toLong()) == null) {
            botService.add(botId)
            Trace.info("发现了新Bot, 已添加Bot($botId)至数据库。")
        }

        ONEBOT.groupRelation.groups.toList().forEach {
            val groupID = it.id.toLong()
            groupCache.add(it.id.toLong())
            if (groupService.read(groupID) == null) {
                groupService.add(GroupExposed(null, groupID, 0.0, 0.0, null, null))
                Trace.info("发现了新群聊, 已添加群聊($groupID)至数据库。")
            }
        }
    }

    private fun loadTFIDF() {
        Trace.info("TF-IDF 加载中...")
        WordDictionary.getInstance().loadDict()
        FinalSeg.getInstance()
        TFIDF.init()
    }

    companion object {
        val LOG_LEVEL: Level = Level.ALL
        val TFIDF = TFIDFAnalyzer()
        var HAN_LP: HanLPClient = HanLPClient("https://www.hanlp.com/api", Config.getValue().module.chat.hanLPToken)
        lateinit var ONEBOT: OneBotBot
        val CRASH = false // 是否在发生错误时崩溃
        val DATABASE_DEBUG = false // 数据库调试
    }
}