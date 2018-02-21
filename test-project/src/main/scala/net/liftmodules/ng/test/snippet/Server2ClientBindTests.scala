package net.liftmodules.ng
package test.snippet

import Angular._
import net.liftweb.common.Empty
import net.liftweb.http.{S, SessionVar}
import net.liftweb.json.DefaultFormats
import net.liftweb.util.Schedule
import net.liftweb.util.Helpers.TimeSpan

object Server2ClientBindTests {
  case class ListWrap(l:List[String] = List.empty[String]) {
    def :+ (a:String) = ListWrap(l :+ a)
  }
  case class Counter(current:Int)

  object array extends SessionVar[ListWrap](ListWrap())

  def optimized = render("ArrayOptimizedBindActor")
  def standard = render("ArrayStandardBindActor")

  def render(cometName:String) = {
    implicit val formats = DefaultFormats
    var counting = false
    var count = 0

    val session = S.session.openOrThrowException("Piss off, Lou!")

    def schedule:Unit = Schedule(() => {
      if(counting) {
        session.findComet("CounterSessionBindActor", Empty).foreach( _ ! Counter(count) )
        count += 1
      }
      schedule
    }, TimeSpan(1000))

    schedule

    renderIfNotAlreadyDefined(angular.module("S2cBindServices").factory("counterService", jsObjFactory()
      .defAny("toggle", {
        counting = !counting
        Empty
      })
    ).factory("arrSvc", jsObjFactory()
      .defAny("next", {
        array.update(_ :+ (new java.util.Date().toString))
        session.findComet(cometName, Empty).foreach( _ ! array.is )
        Empty
      })
    ))
  }
}
