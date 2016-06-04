package com.example

import akka.actor.Actor.Receive
import akka.actor.{Actor, ActorLogging, ActorRef, Cancellable, PoisonPill, Props, Scheduler}
import com.example.Monkey.{AckOnTheRope, PermissionGranted}
import com.example.Rope._

import scala.concurrent.duration._
import scala.collection.immutable.{Queue, Set}

/**
  * Created by mcalavera81 on 01/06/16.
  */
class Rope(maxMonkeySpacing: Int, enableReporting:Boolean, scheduler: Scheduler) extends Actor with ActorLogging{
  import context._

  var waitingMonkeys:List[MonkeySummary] = List[MonkeySummary]()
  var monkeysOnRope = Set[MonkeySummary]()
  var monkeysGettingOnRope = Set[MonkeySummary]()
  var currentSide:Option[Side] =None
  var monkeyTimeout:Option[Cancellable] = None
  var reportTimer:Option[Cancellable] = None
  var startTime:Option[Long] = None

  def receive = initialState


  override  def preStart()={
    startTime = Some(System.currentTimeMillis())
  }


  def initialState: Receive ={
    case Waiting(monkeyId, side) =>
      log.info(s"${getRelativeTime()} sec. Monkey $monkeyId from $side has arrived at the canyon. Waiting for permission to cross...")

      context.become(runningSimulation)
      currentSide = Some(side)
      grantingPermissionToMonkey(MonkeySummary(sender(), monkeyId, side))

  }

  def runningSimulation: Receive = {

    case Waiting(monkeyId, monkeySide) =>
      log.info(s"${getRelativeTime()} sec. Monkey $monkeyId from $monkeySide has arrived at the canyon. Waiting for permission to cross...")

      addMonkeyToWaitingList(sender(), monkeyId, monkeySide)

      if (isRopeFreeFromMonkeys()) { //Pick first on the list
        log.info("Rope is free from monkeys. Picking first on the list")
        grantingPermissionToMonkey(waitingMonkeys.head)
      } else{
        grantPermissionToMonkeyFromCurrentSideIfAny()
      }


    case UsingRope(monkeyId, side) =>
      gettingMonkeyOnRope(MonkeySummary(sender(), monkeyId, side))
      grantPermissionToMonkeyFromCurrentSideIfAny()


    case LeavingRope(monkeyId, side) =>
      gettingMonkeyOffRope(MonkeySummary(sender(), monkeyId, side))
      restartMonkeyTimeout()
      if(isRopeFreeFromMonkeys()){
        findMonkeyWaitingOnSide(currentSide.map(_.opposite)) match {
          //Monkeys from the other side are waiting. Let's avoid starvation by changing direction
          case Some(monkeyOnTheOtherSide)=>
            switchSide()
            grantingPermissionToMonkey(monkeyOnTheOtherSide)
          case None=>
        }
      }

    case MonkeyTimeout =>
      log.info("No more monkeys to cross the canyon!")
      cleanUpTimers()
      context.system.shutdown()

  }

  //Helpers
  def grantPermissionToMonkeyFromCurrentSideIfAny()={
    findMonkeyWaitingOnSide(currentSide.map(_.opposite)) match {
      //There are no monkeys waiting on the other side. We can send one more from the same side if there is any
      case None =>
        findMonkeyWaitingOnSide(currentSide).foreach(grantingPermissionToMonkey)
      case _ => //We do nothing
    }
  }

  def isRopeFreeFromMonkeys():Boolean = monkeysOnRope.isEmpty && monkeysGettingOnRope.isEmpty

  def grantingPermissionToMonkey(monkey: MonkeySummary) ={
    waitingMonkeys =waitingMonkeys.filter(_.monkeyId != monkey.monkeyId)
    monkey.actorRef ! PermissionGranted
    monkeysGettingOnRope += monkey
    log.info(s"${getRelativeTime()} sec. Monkey ${monkey.monkeyId} on ${monkey.side} is getting on the rope.")
    statusReport()
  }

  def gettingMonkeyOnRope(monkey: MonkeySummary)={
    monkeysGettingOnRope -= monkey
    monkeysOnRope += monkey
    monkey.actorRef ! AckOnTheRope
    log.info(s"${getRelativeTime()} sec. Monkey ${monkey.monkeyId} on ${monkey.side} is starting to cross the canyon.")
    statusReport()
  }

  def gettingMonkeyOffRope(monkeyOnRope: MonkeySummary)={
    monkeysOnRope -= monkeyOnRope
    monkeyOnRope.actorRef ! PoisonPill
    log.info(s"${getRelativeTime()} sec. Monkey ${monkeyOnRope.monkeyId} from ${monkeyOnRope.side} has just crossed the canyon.")
    statusReport()
  }

  def addMonkeyToWaitingList(actorRef: ActorRef, monkeyId: String, side:Side)=
    waitingMonkeys = waitingMonkeys :+ MonkeySummary(actorRef,monkeyId, side)

  def findMonkeyWaitingOnSide(sideOpt: Option[Side]):Option[MonkeySummary]=
    sideOpt.flatMap(side => waitingMonkeys.find(_.side == side))

  def switchSide() = {
    currentSide = currentSide.map(_.opposite)
  }

  def restartMonkeyTimeout() = {
    monkeyTimeout.map(_.cancel())
    monkeyTimeout = Some(system.scheduler.scheduleOnce(maxMonkeySpacing second, self,  MonkeyTimeout))
  }

  def statusReport()={
    if(monkeysOnRope.map(_.side).toList.distinct.size > 1) throw new RuntimeException("DeadLock!")

    if(enableReporting){
      log.info(s"\n\t******** Status Report ********")
      log.info(s"\tRelative time: ${getRelativeTime()}")
      log.info(s"\tCurrent side crossing: ${currentSide.getOrElse("")}")
      log.info(s"\tMonkeys on the rope: ${monkeysOnRope.mkString("<-->")}")
      log.info(s"\tMonkeys getting on the rope: ${monkeysGettingOnRope.mkString("<-->")}")
      log.info(s"\tMonkeys waiting: ${waitingMonkeys.mkString("<-->")}")
      log.info(s"\t******** End Report ********\n")
    }
  }

  def cleanUpTimers()= {
    monkeyTimeout.map(_.cancel())
  }

  def getRelativeTime()=Math.round((System.currentTimeMillis()-startTime.get)/1000f)


}

object Rope{

  sealed trait Side{
    def opposite:Side
  }
  case object West extends Side {
    val opposite = East
  }
  case object East extends Side {
    val opposite = West
  }

  case class Waiting(monkeyId: String, side: Side)
  case class UsingRope(monkeyId: String, side: Side)
  case class LeavingRope(monkeyId: String,side: Side)

  case class MonkeySummary(actorRef: ActorRef, monkeyId: String, side: Side){
    override def equals(that: Any) = {
      that match {
        case otherMonkey: MonkeySummary => otherMonkey.monkeyId ==  monkeyId
        case _ => false
      }
    }

    override def hashCode(): Int = monkeyId.hashCode
    override def toString():String = s"Monkey($monkeyId,$side)"
  }

  case object MonkeyTimeout

  def props(maxMonkeySpacing: Int, enableReporting:Boolean, scheduler: Scheduler): Props =
    Props(classOf[Rope], maxMonkeySpacing,enableReporting, scheduler)
}
