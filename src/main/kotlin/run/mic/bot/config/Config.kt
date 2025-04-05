package run.mic.bot.config

import com.google.gson.GsonBuilder
import run.mic.bot.Trace
import run.mic.bot.config.data.ConfigBase
import java.io.File
import kotlin.system.exitProcess

object Config {
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val configFile = File("config.json")

    fun read(): ConfigBase {
        Trace.info("开始读取配置文件")

        if (!configFile.exists() || !configFile.isFile) {
            Trace.warn("配置文件不存在，创建默认配置文件")
            save()
        }

        return gson.fromJson(configFile.readText(), ConfigBase::class.java)
    }

    fun save() {
        if (!configFile.createNewFile()) {
            Trace.error("创建配置文件失败，程序将退出。")
            exitProcess(1)
        }

        configFile.writeText(gson.toJson(ConfigBase(), ConfigBase::class.java))
    }

    fun getValue(): ConfigBase {
        if (!configFile.exists() || !configFile.isFile) {
            save()
        }

        return gson.fromJson(configFile.readText(), ConfigBase::class.java)
    }

}