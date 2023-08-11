package net.rakjarz.corn

interface LogFormat {
    fun format(log: Message): String
}
