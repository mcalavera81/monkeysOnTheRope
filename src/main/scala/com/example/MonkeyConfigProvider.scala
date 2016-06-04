package com.example

import scala.concurrent.duration._
/**
  * Created by mcalavera81 on 03/06/16.
  */
trait MonkeyConfigProvider {

  def timeGetOnRope :FiniteDuration
  def timeToCross :FiniteDuration
}

trait FileMonkeyConfigProvider extends MonkeyConfigProvider{

  val timeGetOnRope =AppConfig.timeGetOnRope seconds
  val timeToCross = AppConfig.timeToCross seconds

}
