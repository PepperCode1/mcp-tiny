/*
 * Copyright (c) 2019, 2020 shedaniel
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package me.shedaniel.mcptiny.logging

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.pattern.color.ANSIConstants
import ch.qos.logback.core.pattern.color.ForegroundCompositeConverterBase

class HighlightingCompositeConverterEx : ForegroundCompositeConverterBase<ILoggingEvent>() {
    override fun getForegroundColorCode(event: ILoggingEvent?): String {
        return when (event?.level) {
            Level.ERROR -> ANSIConstants.RED_FG
            Level.WARN -> ANSIConstants.YELLOW_FG
            Level.INFO -> ANSIConstants.CYAN_FG
            Level.DEBUG -> ANSIConstants.WHITE_FG
            Level.TRACE -> ANSIConstants.WHITE_FG
            else -> ANSIConstants.DEFAULT_FG
        }
    }
}