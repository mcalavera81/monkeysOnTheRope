package com.example

import akka.actor.{ActorRef, ActorSystem, Scheduler}
import akka.event.Logging
import com.example.Monkey.GetToCanyon
import com.example.Rope.{East, Side, West}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

object ApplicationMain extends App{


  val r = scala.util.Random
  val system = ActorSystem("MyActorSystem")

  import system.log
  var scheduler = system.scheduler

  val maxMonkeySpacing = AppConfig.maxMonkeySpacing
  val enableReporting = AppConfig.enableReporting

  def nextETA(): Int= r.nextInt(maxMonkeySpacing-1) + 1

  val numberOfMonkeys = AppConfig.totalMonkeys
  assert(numberOfMonkeys > 0)

  val durations = (1 until numberOfMonkeys).map(_ => nextETA).toList


  val delays= durations.foldLeft[List[Int]](List(3)){(acc,elem)=> {
    (elem+acc.headOption.getOrElse(0)) :: acc
  }}

  val rope: ActorRef = system.actorOf(Rope.props(maxMonkeySpacing, enableReporting, scheduler), "rope")

  def  getSide(delay:Int):Side = if(delay%2==0) West else East


  def scheduleMonkeys(side:Side, delays: List[Int])={
    log.info(s"Monkeys from $side side")
    delays.foreach(delay => {
      val monkeyId = s"Monkey-$delay"


      val monkey= system.actorOf(Monkey.props(classOf[FileMonkey], rope, monkeyId, getSide(delay), scheduler),s"Monkey-$delay")
      system.scheduler.scheduleOnce(delay seconds, monkey,  GetToCanyon)
      log.info(s"Spawned $monkeyId arriving in ${delay} sec.")
    })
    log.info("----------\n")
  }

  log.info("Deploying rope and spawning monkeys...\n")
  val (eastMonkeys, westMonkeys) = delays.partition(getSide(_)==East)
  scheduleMonkeys(East, eastMonkeys)
  scheduleMonkeys(West, westMonkeys)

  system.awaitTermination()
}