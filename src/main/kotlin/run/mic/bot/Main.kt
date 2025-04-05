package run.mic.bot

import run.mic.bot.config.Config

fun main() {
    Bot(Config.read())
}