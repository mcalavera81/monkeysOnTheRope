package com.example

import com.typesafe.config.{Config, ConfigFactory}

/**
  * Created by mcalavera81 on 03/06/16.
  */
object AppConfig {
  private val config = ConfigFactory.load()

  private val monkeyConfig  = config.getConfig("app.monkey")
  val timeGetOnRope = monkeyConfig.getInt("timeGetOnRope")
  val timeToCross = monkeyConfig.getInt("timeToCross")


  private val ropeConfig  = config.getConfig("app.rope")
  val enableReporting = ropeConfig.getBoolean("enableReporting")
  val totalMonkeys = ropeConfig.getInt("totalMonkeys")
  val maxMonkeySpacing = ropeConfig.getInt("maxMonkeySpacing")


}

