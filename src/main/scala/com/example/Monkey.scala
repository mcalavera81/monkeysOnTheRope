package com.example

import akka.actor.{Actor, ActorLogging, ActorRef, Props, Scheduler}
import akka.actor.Actor.Receive
import com.example.Monkey._
import com.example.Rope.Side

import scala.concurrent.duration._


case class FileMonkey(rope: ActorRef,monkeyId: String, side: Side, scheduler: Scheduler) extends Monkey with FileMonkeyConfigProvider

trait Monkey extends Actor with ActorLogging{
  this: MonkeyConfigProvider =>

  def rope: ActorRef
  def monkeyId: String
  def side: Side
  def scheduler: Scheduler

  import context._


  var status:MonkeyStatus = Idle

  def receive = monkeyIdle orElse getStatus

  def monkeyIdle: Receive = {

    case GetToCanyon =>
      status = Waiting
      rope ! Rope.Waiting(monkeyId, side)
      context.become(waitingForPermissionToGetOnTheRope orElse getStatus)
  }

  def waitingForPermissionToGetOnTheRope :Receive ={
    case PermissionGranted =>
      status = GettingOnTheRope
      context.become(gettingOnTheRope orElse getStatus)
      scheduler.scheduleOnce(timeGetOnRope , self,  ReadyToCrossCanyon)
  }

  def gettingOnTheRope :Receive ={
    case ReadyToCrossCanyon =>
      rope ! Rope.UsingRope(monkeyId, side)

    case AckOnTheRope =>
      status = CrossingCanyon
      context.become(crossingCanyon orElse getStatus)
      scheduler.scheduleOnce(timeToCross , self,  Arrival)
  }

  def crossingCanyon:Receive={
    case Arrival =>
      status = OffTheRope
      rope ! Rope.LeavingRope(monkeyId,side)
  }

  def getStatus:Receive ={
    case Status =>
      sender() ! status
  }

}

object Monkey{

  sealed trait MonkeyStatus

  case object Idle extends MonkeyStatus
  case object Waiting extends MonkeyStatus
  case object GettingOnTheRope extends MonkeyStatus
  case object CrossingCanyon extends MonkeyStatus
  case object OffTheRope extends MonkeyStatus


  case object PermissionGranted
  case object GetToCanyon
  case object ReadyToCrossCanyon
  case object Arrival
  case object AckOnTheRope
  case object Status


  def props(clazz: Class[_], rope:ActorRef, monkeyId: String, side: Side, scheduler: Scheduler): Props =
    Props(clazz, rope, monkeyId, side, scheduler)

}


