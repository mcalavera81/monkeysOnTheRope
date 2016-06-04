package com.example

import akka.actor.{ActorRef, ActorSystem, Scheduler}
import akka.testkit.{DefaultTimeout, ImplicitSender, TestActors, TestKit, TestProbe}
import com.example.Monkey.{Status, _}
import com.example.Rope.{LeavingRope, Side, UsingRope, West}
import com.miguno.akka.testing.VirtualTime
import com.typesafe.config.ConfigFactory
import org.scalatest._

import scala.concurrent.duration._


class MonkeyActorSpec(_system: ActorSystem)
  extends TestKit(_system)
    with ImplicitSender
    with FunSpecLike
    with GivenWhenThen
    with Matchers
    with DefaultTimeout
    with MonkeyBuilder
    with BeforeAndAfter
    with BeforeAndAfterAll {

  def this() = this(ActorSystem("MonkeyActorSpec",
                                    ConfigFactory.parseString("""
                                          akka.loggers = ["akka.testkit.TestEventListener"]
                                          akka.stdout-loglevel = "OFF"
                                          akka.loglevel = "OFF"
                                    """)))


  val time = new VirtualTime
  val rope = TestProbe()
  val monkey = createMonkey(West)

  val monkeyActor = system.actorOf(Monkey.props(classOf[TestMonkey], rope.ref, monkey._1, monkey._2, time.scheduler),"monkey")


  describe("A Monkey") {
    it("interacts with the rope according to certain rules") {

      new TestMonkeyConfigProvider {
        var expectedStatus:MonkeyStatus = Idle

        within(50 milliseconds){


          monkeyActor ! Status
          expectedStatus = Idle
          expectMsg(expectedStatus)
          info(s"Monkey status: $expectedStatus")

          monkeyActor ! GetToCanyon

          rope.expectMsg(Rope.Waiting.tupled(monkey))


          monkeyActor ! Status
          expectedStatus = Waiting
          expectMsg(expectedStatus)
          info(s"Monkey status: $expectedStatus")


          monkeyActor ! PermissionGranted
          monkeyActor ! Status
          expectedStatus = GettingOnTheRope
          expectMsg(expectedStatus)
          info(s"Monkey status: $expectedStatus")


          time.advance(timeGetOnRope)
          rope.expectMsg(UsingRope.tupled(monkey))

          monkeyActor ! AckOnTheRope

          monkeyActor ! Status
          expectedStatus = CrossingCanyon
          expectMsg(expectedStatus)
          info(s"Monkey status: $expectedStatus")

          time.advance(timeToCross)
          rope.expectMsg(LeavingRope.tupled(monkey))

          monkeyActor ! Status
          expectedStatus = OffTheRope
          expectMsg(expectedStatus)
          info(s"Monkey status: $expectedStatus")
        }

      }

    }
  }
}

trait TestMonkeyConfigProvider extends MonkeyConfigProvider{
  val timeGetOnRope = 1 milliseconds
  val timeToCross = 4 milliseconds
}

class TestMonkey(var rope: ActorRef,var monkeyId: String, var side: Side, var scheduler: Scheduler) extends Monkey with TestMonkeyConfigProvider
