package com.eooog.rushseat

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.web.socket.config.annotation.EnableWebSocket

@EnableScheduling
@EnableWebSocket
@SpringBootApplication
class RushSeatApplication

fun main(args: Array<String>) {
    runApplication<RushSeatApplication>(*args)
}
