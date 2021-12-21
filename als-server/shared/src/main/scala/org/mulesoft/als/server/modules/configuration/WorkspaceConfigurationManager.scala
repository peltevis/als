package org.mulesoft.als.server.modules.configuration

import org.mulesoft.als.server.RequestModule
import org.mulesoft.als.server.feature.configuration.workspace._
import org.mulesoft.als.logger.Logger
import org.mulesoft.als.server.modules.workspace.WorkspaceContentManager
import org.mulesoft.als.server.workspace.WorkspaceManager
import org.mulesoft.als.server.workspace.extract.WorkspaceConfig
import org.mulesoft.lsp.ConfigType
import org.mulesoft.lsp.feature.telemetry.MessageTypes.MessageTypes
import org.mulesoft.lsp.feature.telemetry.{MessageTypes, TelemetryProvider}
import org.mulesoft.lsp.feature.{RequestType, TelemeteredRequestHandler}
import org.mulesoft.lsp.textsync.KnownDependencyScopes._
import org.mulesoft.lsp.textsync.{DependencyConfiguration, DidChangeConfigurationNotificationParams}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class WorkspaceConfigurationManager(val workspaceManager: WorkspaceManager,
                                    private val telemetryProvider: TelemetryProvider,
                                    private val logger: Logger)
    extends RequestModule[WorkspaceConfigurationClientCapabilities, WorkspaceConfigurationOptions]
    with WorkspaceConfigurationProvider {

  private var getEnabled = true
  override def getRequestHandlers: Seq[TelemeteredRequestHandler[_, _]] =
    Seq(new GetWorkspaceConfigurationRequestHandler(this, telemetryProvider))

  override def applyConfig(config: Option[WorkspaceConfigurationClientCapabilities]): WorkspaceConfigurationOptions = {
    getEnabled = config.forall(_.get)
    WorkspaceConfigurationOptions(true)
  }
  override val `type`: ConfigType[WorkspaceConfigurationClientCapabilities, WorkspaceConfigurationOptions] =
    WorkspaceConfigurationConfigType

  override def initialize(): Future[Unit] = Future.successful()

  def getWorkspaceConfiguration(uri: String): Future[(WorkspaceContentManager, Option[WorkspaceConfig])] =
    workspaceManager
      .getWorkspace(uri)
      .flatMap(w => w.getCurrentConfiguration.map(c => (w, c)))
}

class GetWorkspaceConfigurationRequestHandler(val provider: WorkspaceConfigurationProvider,
                                              private val telemetryProvider: TelemetryProvider)
    extends TelemeteredRequestHandler[GetWorkspaceConfigurationParams, GetWorkspaceConfigurationResult] {

  override protected def telemetry: TelemetryProvider = telemetryProvider

  override protected def task(params: GetWorkspaceConfigurationParams): Future[GetWorkspaceConfigurationResult] =
    provider
      .getWorkspaceConfiguration(params.textDocument.uri)
      .map(t =>
        GetWorkspaceConfigurationResult(
          t._1.folderUri,
          t._2
            .map(config =>
              DidChangeConfigurationNotificationParams(
                config.mainFile,
                Some(t._1.folderUri),
                config.cachables.map(Left(_))
                  ++ config.profiles.map(p => Right(DependencyConfiguration(p, CUSTOM_VALIDATION)))
                  ++ config.semanticExtensions.map(p => Right(DependencyConfiguration(p, SEMANTIC_EXTENSION)))
            ))
            .getOrElse(new EmptyConfigurationParams(t._1.folderUri))
      ))

  override protected def code(params: GetWorkspaceConfigurationParams): String = "GetWorkspaceConfigurationRequest"

  override protected def beginType(params: GetWorkspaceConfigurationParams): MessageTypes =
    MessageTypes.BEGIN_GET_WORKSPACE_CONFIGURATION

  override protected def endType(params: GetWorkspaceConfigurationParams): MessageTypes =
    MessageTypes.END_GET_WORKSPACE_CONFIGURATION

  override protected def msg(params: GetWorkspaceConfigurationParams): String =
    s"Getting workspace configuration for workspace containing uri ${params.textDocument.uri}"

  override protected def uri(params: GetWorkspaceConfigurationParams): String = params.textDocument.uri

  override def `type`: RequestType[GetWorkspaceConfigurationParams, GetWorkspaceConfigurationResult] =
    GetWorkspaceConfigurationRequestType

  override protected val empty: Option[GetWorkspaceConfigurationResult] = None
}

private class EmptyConfigurationParams(workspaceFolder: String)
    extends DidChangeConfigurationNotificationParams("", Some(workspaceFolder), Set.empty)