package org.mulesoft.als.server.workspace.extract

import amf.core.remote.Platform
import amf.internal.environment.Environment
import org.mulesoft.als.server.modules.workspace.WorkspaceContentManager

import scala.concurrent.Future

trait WorkspaceConfigurationProvider {
  def obtainConfiguration(platform: Platform, environment: Environment): Future[Option[WorkspaceConf]]
}

case class DefaultWorkspaceConfigurationProvider(manager: WorkspaceContentManager,
                                                 mainUri: String,
                                                 dependencies: Set[String],
                                                 reader: Option[ConfigReader])
    extends WorkspaceConfigurationProvider {
  override def obtainConfiguration(platform: Platform, environment: Environment): Future[Option[WorkspaceConf]] =
    Future.successful(
      Some(
        WorkspaceConf(
          manager.folder,
          mainUri.stripPrefix(manager.folder).stripPrefix("/"), // just the file name, not the full path
          dependencies,
          reader
        )))
}

case class ReaderWorkspaceConfigurationProvider(manager: WorkspaceContentManager)
    extends WorkspaceConfigurationProvider {
  override def obtainConfiguration(platform: Platform, environment: Environment): Future[Option[WorkspaceConf]] = {
    manager.workspaceConfiguration.flatMap(_.configReader) match {
      case Some(configReader) =>
        configReader.readRoot(manager.folder, platform, environment)
      case _ => Future.successful(None)
    }
  }
}