package org.mulesoft.als.server.workspace.command

import amf.core.internal.parser._
import org.mulesoft.als.configuration.ProjectConfiguration
import org.mulesoft.als.logger.Logger
import org.mulesoft.als.server.workspace.WorkspaceManager
import org.mulesoft.lsp.textsync.KnownDependencyScopes._
import org.mulesoft.lsp.textsync.{DependencyConfiguration, DidChangeConfigurationNotificationParams}
import org.yaml.model.{YMap, YMapEntry, YScalar, YSequence}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class DidChangeConfigurationCommandExecutor(wsc: WorkspaceManager)
    extends CommandExecutor[DidChangeConfigurationNotificationParams, Unit] {

  override protected def treatParams(arguments: List[String]): List[String] =
    arguments
      .map(StringContext.treatEscapes)
      .map(s =>
        if (s.startsWith("\"") && s.endsWith("\"")) {
          s.substring(1, s.length - 1)
        } else s
      )

  override protected def buildParamFromMap(m: YMap): Option[DidChangeConfigurationNotificationParams] = {
    val mainPath: Option[String] = m.key("mainPath").flatMap(e => e.value.toOption[String])
    val folder: String = m
      .key("folder")
      .flatMap(e => e.value.toOption[String])
      .orElse(mainPath)
      .getOrElse({
        Logger.error(
          "Change configuration command with no folder value or mainPath",
          "DidChangeConfigurationCommandExecutor",
          "buildParamFromMap"
        )
        ""
      })
    val dependencies: Set[Either[String, DependencyConfiguration]] =
      m.key("dependencies").map(seqToSet).getOrElse(Set.empty)

    Some(DidChangeConfigurationNotificationParams(mainPath, folder, dependencies))
  }

  private def extractDependencyConfiguration(m: YMap): DependencyConfiguration = {
    DependencyConfiguration(
      m.key("file").flatMap(_.value.toOption[String]).getOrElse(""),
      m.key("scope").flatMap(_.value.toOption[String]).getOrElse("")
    )

  }

  private def seqToSet(entry: YMapEntry): Set[Either[String, DependencyConfiguration]] = {
    entry.value.value match {
      case seq: YSequence =>
        seq.nodes
          .map(_.value)
          .map {
            case s: YScalar => Left(s.text)
            case m: YMap    => Right(extractDependencyConfiguration(m))
          }
          .toSet
      case _ => Set.empty
    }
  }

  override protected def runCommand(param: DidChangeConfigurationNotificationParams): Future[Unit] =
    wsc.getWorkspace(param.folder).flatMap { manager =>
      Logger.debug(
        s"DidChangeConfiguration for workspace @ ${manager.folderUri} (folder: ${param.folder}, mainPath:${param.mainPath})",
        "DidChangeConfigurationCommandExecutor",
        "runCommand"
      )
      val projectConfiguration = new ProjectConfiguration(
        param.folder,
        param.mainPath,
        param.dependencies
          .filterNot(d =>
            d.isRight && d.right.exists(r => Set(CUSTOM_VALIDATION, SEMANTIC_EXTENSION, DIALECT).contains(r.scope))
          )
          .map {
            case Left(v)  => v
            case Right(v) => v.file
          },
        extractPerScope(param, CUSTOM_VALIDATION),
        extractPerScope(param, SEMANTIC_EXTENSION),
        extractPerScope(param, DIALECT)
      )
      Logger.debug(
        s"Workspace '${projectConfiguration.folder}' new configuration { mainFile: ${projectConfiguration.mainFile}, dependencies: ${projectConfiguration.designDependency}, profiles: ${projectConfiguration.validationDependency} }",
        "WorkspaceManager",
        "contentManagerConfiguration"
      )
      manager.withConfiguration(projectConfiguration)

    }

  private def extractPerScope(param: DidChangeConfigurationNotificationParams, scope: String): Set[String] =
    param.dependencies
      .flatMap {
        case Right(v) if v.scope == scope => Some(v.file)
        case _                            => None
      }
}
