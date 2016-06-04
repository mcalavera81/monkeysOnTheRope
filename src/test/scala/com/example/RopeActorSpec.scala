package com.example

import akka.actor.{Actor, ActorSystem, PoisonPill, Props}
import akka.testkit.{DefaultTimeout, ImplicitSender, TestActorRef, TestActors, TestKit}
import com.example.Rope._
import com.miguno.akka.testing.VirtualTime
import org.scalatest._

import scala.concurrent.duration._
import akka.pattern.ask
import akka.util.Timeout
import com.example.Monkey.{AckOnTheRope, Arrival, PermissionGranted}
import com.typesafe.config.ConfigFactory

import scala.concurrent.Future
import scala.util.Success


class RopeActorSpec(_system: ActorSystem)
  extends TestKit(_system)
    with ImplicitSender
    with FeatureSpecLike
    with GivenWhenThen
    with Matchers
    with DefaultTimeout
    with MonkeyBuilder
    with BeforeAndAfter
    with BeforeAndAfterAll {

  def this() = this(ActorSystem("RopeActorSpec",
                                    ConfigFactory.parseString("""
                                          akka.loggers = ["akka.testkit.TestEventListener"]
                                          akka.stdout-loglevel = "OFF"
                                          akka.loglevel = "OFF"
                                    """)))

  var time: VirtualTime = _
  var ropeActor: TestActorRef[Rope] = _
  var underlyingActor: Rope = _

  before {
    time = new VirtualTime
    ropeActor = TestActorRef(Rope.props(7, false, time.scheduler))
    underlyingActor = ropeActor.underlyingActor
  }

  feature("The Rope actor should handle monkey actor messages appropriately") {

    scenario("A monkey asks for permission to cross the canyon given an empty rope") {

      Given("An empty rope and a monkey on the West side")
      val monkey = createMonkey(West)

      When("The monkey asks for permission to cross the canyon")
      val responseToWaiting = ropeActor ? Waiting.tupled(monkey)

      Then("The rope grants him permission to cross")
      responseToWaiting.value.get should be(Success(PermissionGranted))

      And("There should be a monkey getting on the rope and none on the rope yet")
      underlyingActor.monkeysGettingOnRope.head.monkeyId should be(monkey._1)
      underlyingActor.monkeysOnRope.size should be(0)
    }


    scenario("A monkey granted permission to cross, goes on to start crossing the canyon") {

      Given("A monkey from the West with permission to cross")
      val monkey = createMonkey(West)
      val responseToWaiting = ropeActor ? Waiting.tupled(monkey)
      responseToWaiting.value.get should be(Success(PermissionGranted))

      When("The monkey gets on the rope")
      val responseToUsingRope = ropeActor ? UsingRope.tupled(monkey)

      Then("There should be a monkey already on the rope and none getting on the rope")
      underlyingActor.monkeysGettingOnRope.size should be(0)
      underlyingActor.monkeysOnRope.size should be(1)

      And("The rope confirms that the monkey is on the rope")
      responseToUsingRope.value.get should be(Success(AckOnTheRope))

    }

    scenario("A monkey has finished its journey across the canyon, and gets off the rope") {

      Given("A monkey on the rope")
      val monkey = createMonkey(West)
      ropeActor ? Waiting.tupled(monkey)
      ropeActor ? UsingRope.tupled(monkey)

      When("The monkey tries to leave the rope")
      val responseToLeavingRope = ropeActor ? LeavingRope.tupled(monkey)

      Then("The rope should be empty")
      underlyingActor.monkeysGettingOnRope.size should be(0)
      underlyingActor.monkeysOnRope.size should be(0)

      And("The monkey should get a PoisonPill")
      responseToLeavingRope.value.get should be(Success(PoisonPill))

    }
    scenario("A couple of monkeys on the rope from the same side") {

      Given("A monkey from West getting on the rope and a second monkey also from West")
      val monkeyWestGettingOnRope = createMonkey(West)
      ropeActor ? Waiting.tupled(monkeyWestGettingOnRope)
      val monkeyWest = createMonkey(West)

      When("The second monkey tries to join the rope")
      val response = ropeActor ? Waiting.tupled(monkeyWest)

      Then("The rope grants him permission to cross")
      response.value.get should be(Success(PermissionGranted))

      And("There should be two monkey getting on the rope ")
      underlyingActor.monkeysGettingOnRope.size should be(2)
    }

    scenario("A couple of monkeys from opposite sides") {

      Given("A monkey from West getting on the rope and a second monkey from East")
      val monkeyWestGettingOnRope = createMonkey(West)
      ropeActor ? Waiting.tupled(monkeyWestGettingOnRope)
      val monkeyEast = createMonkey(East)

      When("The second monkey tries to join the rope")
      val response = ropeActor ? Waiting.tupled(monkeyEast)

      Then("The rope does not grant him permission to cross")
      response.value should be(None)

      And("There should be only one monkey near the rope ")
      underlyingActor.monkeysGettingOnRope.head.monkeyId should be(monkeyWestGettingOnRope._1)
      underlyingActor.monkeysOnRope.size should be(0)

    }

    scenario("Avoiding starvation part-1") {
      Given("A monkey on the rope, one waiting on the opposite side")
      val monkeyWestOnRope = createMonkey(West)
      ropeActor ? Waiting.tupled(monkeyWestOnRope)

      val monkeyEastWaiting = createMonkey(East)
      ropeActor ? Waiting.tupled(monkeyEastWaiting)
      underlyingActor.waitingMonkeys.size should be(1)

      When("Another monkey tries to join the rope from the same side the monkey on the rope is coming from")
      val monkeyWest = createMonkey(West)
      val monkeyWestWaiting = ropeActor ? Waiting.tupled(monkeyWest)

      Then("In order to avoid starvation, the rope prioritizes the one waiting on the other side, and ")
      And("as a consequence both newcomers are waiting")
      monkeyWestWaiting.value should be(None)
      underlyingActor.monkeysGettingOnRope.size should be(1)
      underlyingActor.monkeysOnRope.size should be(0)
      underlyingActor.waitingMonkeys.size should be(2)

    }

    scenario("Avoiding starvation part-2") {
      Given("A monkey on the rope, and 2 waiting on opposites sides")
      val monkeyWestOnRope = createMonkey(West)
      ropeActor ? Waiting.tupled(monkeyWestOnRope)
      ropeActor ? UsingRope.tupled(monkeyWestOnRope)

      val monkeyEastWaiting = createMonkey(East)
      ropeActor ? Waiting.tupled(monkeyEastWaiting)

      val monkeyWestWaiting = createMonkey(East)
      ropeActor ? Waiting.tupled(monkeyWestWaiting)

      underlyingActor.monkeysOnRope.size should be(1)

      When("The monkey on the rope leaves")
      val responseLeaving = ropeActor ? LeavingRope.tupled(monkeyWestOnRope)
      responseLeaving.value.get should be(Success(PoisonPill))

      underlyingActor.monkeysOnRope.size should be(0)

      Then("The one waiting on the opposite side gets to cross the canyon")
      underlyingActor.monkeysGettingOnRope.size should be(1)
      underlyingActor.monkeysGettingOnRope.head.monkeyId should be(monkeyEastWaiting._1)

    }
  }
}


  /*"A Pong actor" must {
    "send back a pong on a ping" in {
      val pongActor = system.actorOf(PongActor.props)
      pongActor ! PingActor.PingMessage("ping")
      expectMsg(PongActor.PongMessage("pong"))
    }
  }*/


