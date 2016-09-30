package models.tosca

import scalaz._
import Scalaz._
import scalaz.effect.IO
import scalaz.Validation
import scalaz.Validation.FlatMap._
import scalaz.NonEmptyList._
import scalaz.syntax.SemigroupOps

import models.json.tosca._
import models.json.tosca.carton._
import models.Constants._
import io.megam.auth.funnel.FunnelErrors._
import models.base._


class Launcher(authBag: Option[io.megam.auth.stack.AuthBag]) {

  private val trackEye = List[String]()

  def launch(clubbed: String): ValidationNel[Throwable, AssembliesResults] = {
   ((new UnitsBreaker(clubbed).break) map {
       _.map { unit =>
       play.api.Logger.debug(("%-20s -->[%s]").format("Unit "+ (trackEye ++ "⨀")))
       (Assemblies.create(authBag, unit) leftMap { t: NonEmptyList[Throwable] =>
       new ServiceUnavailableError("Not launched", (t.list.map(m => m.getMessage)).mkString("\n"))
       }).toValidationNel.flatMap { xso: AssembliesResult =>
             List(xso.some).successNel[Throwable]
           }
         }
       } map {
         _.foldRight((AssembliesResults.empty).successNel[Throwable])(_ +++ _)
        //the exception is wrong here. we need to figure out the correct one.
        //we have rewritten in 2.0, hence for now hang in there  buddy.
      }).getOrElse(new ResourceItemNotFound("","Not launched.").failureNel[AssembliesResults])
    }
}
