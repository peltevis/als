package org.mulesoft.als.server.modules.diagnostic

import amf._
import amf.core.model.document.BaseUnit
import amf.core.services.RuntimeValidator
import amf.core.validation.AMFValidationReport
import amf.internal.environment.Environment
import org.mulesoft.als.server.client.ClientNotifier
import org.mulesoft.als.server.logger.Logger
import org.mulesoft.als.server.modules.ast._
import org.mulesoft.als.server.modules.common.reconciler.Runnable
import org.mulesoft.amfintegration.{AmfResolvedUnit, DiagnosticsBundle}
import org.mulesoft.amfmanager.AmfImplicits._
import org.mulesoft.lsp.feature.telemetry.{MessageTypes, TelemetryProvider}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Success}

class ResolutionDiagnosticManager(override protected val telemetryProvider: TelemetryProvider,
                                  override protected val clientNotifier: ClientNotifier,
                                  override protected val logger: Logger,
                                  override protected val env: Environment,
                                  override protected val validationGatherer: ValidationGatherer)
    extends ResolvedUnitListener
    with DiagnosticManager {
  type RunType = ValidationRunnable
  override val managerName: DiagnosticManagerKind = ResolutionDiagnosticKind

  protected override def runnable(ast: AmfResolvedUnit, uuid: String) =
    new ValidationRunnable(ast.originalUnit.identifier, ast, uuid)

  protected override def onNewAstPreprocess(resolved: AmfResolvedUnit, uuid: String): Unit = {
    logger.debug("Got new AST:\n" + resolved.originalUnit.id, "ValidationManager", "newASTAvailable")
    telemetryProvider.addTimedMessage("Start report",
                                      "DiagnosticManager",
                                      "onNewAst",
                                      MessageTypes.BEGIN_DIAGNOSTIC,
                                      resolved.originalUnit.identifier,
                                      uuid)
  }

  protected override def onFailure(uuid: String, uri: String, exception: Throwable): Unit = {
    telemetryProvider.addTimedMessage(s"End report: ${exception.getMessage}",
                                      "DiagnosticManager",
                                      "onNewAst",
                                      MessageTypes.END_DIAGNOSTIC,
                                      uri,
                                      uuid)
    logger.error("Error on validation: " + exception.toString, "ValidationManager", "newASTAvailable")
    clientNotifier.notifyDiagnostic(ValidationReport(uri, Set.empty, ProfileNames.AMF).publishDiagnosticsParams)
  }

  protected override def onSuccess(uuid: String, uri: String): Unit =
    telemetryProvider.addTimedMessage("End report",
                                      "DiagnosticManager",
                                      "onNewAst",
                                      MessageTypes.END_DIAGNOSTIC,
                                      uri,
                                      uuid)

  private def gatherValidationErrors(uri: String,
                                     resolved: AmfResolvedUnit,
                                     references: Map[String, DiagnosticsBundle],
                                     uuid: String): Future[Unit] = {
    val startTime = System.currentTimeMillis()

    val profile = profileName(resolved.originalUnit)
    this
      .report(uri, telemetryProvider, resolved, uuid, profile)
      .map(report => {
        val endTime = System.currentTimeMillis()
        validationGatherer
          .indexNewReport(ErrorsWithTree(uri, report.results, Some(tree(resolved.originalUnit))), managerName, uuid)
        notifyReport(uri, resolved.originalUnit, references, managerName, profile)

        this.logger.debug(s"It took ${endTime - startTime} milliseconds to validate",
                          "ValidationManager",
                          "gatherValidationErrors")
      })
  }

  private def tree(baseUnit: BaseUnit): Set[String] =
    baseUnit.flatRefs
      .map(bu => bu.location().getOrElse(bu.id))
      .toSet + baseUnit.location().getOrElse(baseUnit.id)

  private def report(uri: String,
                     telemetryProvider: TelemetryProvider,
                     resolved: AmfResolvedUnit,
                     uuid: String,
                     profile: ProfileName): Future[AMFValidationReport] = {
    telemetryProvider.addTimedMessage("Start AMF report",
                                      "DiagnosticManager",
                                      "report",
                                      MessageTypes.BEGIN_REPORT,
                                      uri,
                                      uuid)
    try {
      resolved.getLast.flatMap { r =>
        r.resolvedUnit.flatMap(RuntimeValidator(_, profile, resolved = true, env = env)).map { vr =>
          AMFValidationReport(vr.conforms, vr.model, vr.profile, vr.results ++ r.eh.getErrors)
        }
      } andThen {
        case _ =>
          telemetryProvider
            .addTimedMessage("End AMF report", "DiagnosticManager", "report", MessageTypes.END_REPORT, uri, uuid)
      } recoverWith {
        case e: Exception =>
          sendFailedClone(uri, telemetryProvider, resolved.originalUnit, uuid, e.getMessage)
      }
    } catch {
      case e: Exception =>
        sendFailedClone(uri, telemetryProvider, resolved.originalUnit, uuid, e.getMessage)
    }
  }

  override def onRemoveFile(uri: String): Unit = {
    validationGatherer.removeFile(uri, managerName)
    clientNotifier.notifyDiagnostic(AlsPublishDiagnosticsParams(uri, Nil, ProfileNames.AMF))
  }

  class ValidationRunnable(var uri: String, ast: AmfResolvedUnit, uuid: String) extends Runnable[Unit] {
    private var canceled = false

    private val kind = "ValidationRunnable"

    def run(): Promise[Unit] = {
      val promise = Promise[Unit]()

      gatherValidationErrors(ast.originalUnit.identifier, ast, ast.diagnosticsBundle, uuid) andThen {
        case Success(report) => promise.success(report)

        case Failure(error) => promise.failure(error)
      }

      promise
    }

    def conflicts(other: Runnable[Any]): Boolean =
      other.asInstanceOf[ValidationRunnable].kind == kind && uri == other.asInstanceOf[ValidationRunnable].uri

    def cancel() {
      canceled = true
    }

    def isCanceled(): Boolean = canceled
  }

}