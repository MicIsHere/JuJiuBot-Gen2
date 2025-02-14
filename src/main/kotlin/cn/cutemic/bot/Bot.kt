package cn.cutemic.bot

import cn.cutemic.bot.event.EventRegistry.initAllEvents
import cn.cutemic.bot.manager.ModuleManager
import cn.cutemic.bot.manager.TaskManager
import cn.cutemic.bot.util.KernelScope
import cn.cutemic.bot.util.runSynchronized
import com.hankcs.hanlp.restful.HanLPClient
import com.huaban.analysis.jieba.WordDictionary
import com.huaban.analysis.jieba.viterbi.FinalSeg
import com.mongodb.kotlin.client.coroutine.MongoClient
import com.qianxinyao.analysis.jieba.keyword.TFIDFAnalyzer
import io.ktor.http.*
import kotlinx.coroutines.launch
import love.forte.simbot.application.Application
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


class Bot {
    private lateinit var app: SimpleApplication

    init {
        LOGGER.info("System init...")
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
                    /// 在OneBot组件中用于区分不同Bot的唯一ID， 建议可以直接使用QQ号。
                    botUniqueId = "3938656042"
                    apiServerHost = Url("http://127.0.0.1:7999")
                    eventServerHost = Url("ws://127.0.0.1:8080/onebot/v11/ws/")
                }
            )

            ModuleManager.perLoadModule()
            this.initAllEvents().let {
                LOGGER.info("Init all module(s) event...")
            }

            LOGGER.info("OneBot is ok.")
            ONEBOT.start()
        }.onFailure {
            LOGGER.fatal("An error occurred while loading the system: ${it.message}")
            it.printStackTrace()
        }
    }

    companion object {
        val LOGGER: Logger = LogManager.getLogger("JuJiuBot").let {
            Configurator.setLevel(it.name, Level.ALL)
            return@let it
        }

        val TFIDF = TFIDFAnalyzer()
        var HAN_LP: HanLPClient = HanLPClient("https://www.hanlp.com/api", "")
        val MONGO_DB = MongoClient.create("mongodb://localhost").getDatabase("JuJiuBot").let {
            LOGGER.info("Connect database success.")
            it
        }

        lateinit var ONEBOT: OneBotBot
    }
}