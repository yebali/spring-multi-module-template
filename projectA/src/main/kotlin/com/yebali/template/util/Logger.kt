package com.yebali.template.util

import io.github.oshai.kotlinlogging.KotlinLogging

abstract class Logger {
    val logger = KotlinLogging.logger(this.javaClass.name)
}
