import com.typesafe.tools.mima.core.ProblemFilters._
import com.typesafe.tools.mima.core._
import com.typesafe.tools.mima.plugin.MimaKeys._
import sbt.Keys.{ name, organization }
import sbt._

object MimaSettings {
  lazy val bincompatVersionToCompare = "2.1.2"

  def mimaSettings(failOnProblem: Boolean) =
    Seq(
      mimaPreviousArtifacts := Set(organization.value %% name.value % bincompatVersionToCompare),
      mimaBinaryIssueFilters ++= Seq(
        exclude[Problem]("zio.logging.internal.*")
      ),
      mimaFailOnProblem     := failOnProblem
    )
}
