package net.liftmodules.ng

import net.liftweb.common.Loggable
import net.liftweb.http.js.JE.{Call, JsRaw, AnonFunc}
import net.liftweb.http.js.JsCmd
import net.liftweb.http.js.JsCmds._
import net.liftweb.json.{Formats, JsonAST, Extraction}
import net.liftweb.util.Props

private [ng] trait LiftNgJsHelpers extends Loggable {
  protected val id:String = Angular.rand

  /** Interval between tries to unload our early-arrival event queue */
  private val interval = Props.getInt("net.liftmodules.ng.AngularActor.retryInterval", 100)

  /** Variable assignment for \$scope */
  private val varElement = JsCrVar("e", AnonFunc("id",
    JsRaw(
      "if(typeof angular==='undefined'||typeof angular.element==='undefined')return void 0;else " +
        "return angular.element(document.querySelector('#'+id))"
    )
  ))
  private val varScope = JsCrVar("s", AnonFunc("id",
    JsRaw(
      "if(typeof e(id)==='undefined')return void 0;else " +
        "return e(id).scope()"
    )
  ))
  /** Variable assignment for \$rootScope */
  private val varRoot  = JsCrVar("r", AnonFunc("id", JsReturn(JsRaw("(typeof e(id)==='undefined')?void 0:e(id).injector().get('$rootScope')"))))

  /** Sends any of our commands with all of the early-arrival retry mechanism packaged up */
  protected def buildCmd(root:Boolean, f:JsCmd):JsCmd = {
    val scopeFn = if(root) "r('"+id+"')" else "s('"+id+"')"
    val vars = varElement & (if(root) varRoot else varScope)
    val ready = JsCrVar("t", AnonFunc(JsReturn(JsRaw("typeof " + scopeFn + "!=='undefined'"))))
    val fn = JsCrVar("f", AnonFunc(Call(scopeFn+".$apply", AnonFunc(f))))
    val dequeue = "var d=function(){" +
      "if(net_liftmodules_ng_q[0].t()){"+
      "for(i=0;i<net_liftmodules_ng_q.length;i++){" +
      "net_liftmodules_ng_q[i].f();"+
      "}"+
      "net_liftmodules_ng_q=void 0;"+
      "}else{"+
      "setTimeout(function(){d();},"+interval+");"+
      "}"+
      "};"
    val enqueue = "if(typeof net_liftmodules_ng_q==='undefined'){net_liftmodules_ng_q=[];setTimeout(function(){d();},"+interval+");}" +
      "net_liftmodules_ng_q.push({t:t,f:f});"
    val cmds = vars & ready & fn & JsRaw(dequeue+"if(typeof net_liftmodules_ng_q==='undefined'&&t()){f();}else{"+enqueue+"}")
    logger.trace(cmds)
    JsRaw("(function(){"+cmds.toJsCmd+"}).call(this)")
  }

  protected def stringify(obj:Any)(implicit formats:Formats):String =
    JsonAST.compactRender(Extraction.decompose(obj)(formats + new LAFutureSerializer))
}
