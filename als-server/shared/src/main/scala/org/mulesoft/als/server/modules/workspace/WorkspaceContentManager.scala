package org.mulesoft.als.server.modules.workspace

import java.util.UUID

import amf.internal.environment.Environment
import org.mulesoft.als.common.FileUtils
import org.mulesoft.als.server.logger.Logger
import org.mulesoft.als.server.modules.ast._
import org.mulesoft.als.server.textsync.EnvironmentProvider
import org.mulesoft.als.server.workspace.UnitTaskManager
import org.mulesoft.als.server.workspace.extract.{WorkspaceConf, WorkspaceConfigurationProvider}
import org.mulesoft.amfmanager.AmfParseResult
import org.mulesoft.lsp.feature.telemetry.{MessageTypes, TelemetryProvider}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class WorkspaceContentManager(val folder: String,
                              environmentProvider: EnvironmentProvider,
                              telemetryProvider: TelemetryProvider,
                              logger: Logger,
                              allSubscribers: List[BaseUnitListener])
    extends UnitTaskManager[ParsedUnit, CompilableUnit, NotificationKind] {

  private val subscribers = allSubscribers.filter(_.isActive)

  private var configMainFile: Option[WorkspaceConf] = None

  def workspaceConfiguration: Option[WorkspaceConf] = configMainFile

  def setConfigMainFile(workspaceConf: Option[WorkspaceConf]): Unit = {
    repository.cleanTree()
    repository.setCachables(workspaceConf.map(_.cachables).getOrElse(Set.empty))
    configMainFile = workspaceConf
  }

  def mainFile: Option[String] = configMainFile.map(_.mainFile)

  def configFile: Option[String] =
    configMainFile.flatMap(ic => ic.configReader.map(cr => s"${ic.rootFolder}/${cr.configFileName}"))

  override protected val stagingArea: ParserStagingArea = new ParserStagingArea(environmentProvider)

  def environment: Environment                                                       = stagingArea.snapshot().environment
  override protected val repository                                                  = new WorkspaceParserRepository(logger)
  private var workspaceConfigurationProvider: Option[WorkspaceConfigurationProvider] = None

  def getCompilableUnit(uri: String): Future[CompilableUnit] = {
    val encodedUri = FileUtils.getEncodedUri(uri, environmentProvider.platform)
    repository.getUnit(encodedUri) match {
      case Some(pu) =>
        Future.successful(toResult(encodedUri, pu))
      case _ => getNext(encodedUri).getOrElse(fail(encodedUri))
    }
  }

  override protected def toResult(uri: String, pu: ParsedUnit): CompilableUnit =
    pu.toCU(getNext(uri), mainFile, repository.getReferenceStack(uri), state == NotAvailable)

  def withConfiguration(confProvider: WorkspaceConfigurationProvider): WorkspaceContentManager = {
    workspaceConfigurationProvider = Some(confProvider)
    this
  }

  override protected def processTask(): Future[Unit] = {
    val snapshot: Snapshot    = stagingArea.snapshot()
    val (treeUnits, isolated) = snapshot.files.partition(u => repository.inTree(u._1)) // what if a new file is added between the partition and the override down
    val changedTreeUnits =
      treeUnits.filter(tu => tu._2 == CHANGE_FILE || tu._2 == CLOSE_FILE)

    if (hasChangedConfigFile(snapshot)) processChangeConfigChanges(snapshot)
    else if (changedTreeUnits.nonEmpty)
      processMFChanges(configMainFile.get.mainFile, snapshot)
    else
      processIsolatedChanges(isolated, snapshot.environment)
  }

  private def hasChangedConfigFile(snapshot: Snapshot) =
    snapshot.files.map(_._2).contains(CHANGE_CONFIG)

  private def processIsolatedChanges(files: List[(String, NotificationKind)], environment: Environment): Future[Unit] = {
    val (closedFiles, changedFiles) = files.partition(_._2 == CLOSE_FILE)
    cleanFiles(closedFiles)

    if (changedFiles.nonEmpty)
      processIsolated(files.head._1, environment, UUID.randomUUID().toString)
    else Future.successful(Unit)
  }

  private def processIsolated(file: String, environment: Environment, uuid: String): Future[Unit] = {
    changeState(ProcessingFile(file))
    stagingArea.dequeue(Set(file))
    parse(file, environment, uuid)
      .map { bu =>
        repository.updateUnit(bu.baseUnit)
        subscribers.foreach(_.onNewAst(BaseUnitListenerParams(bu, Map.empty, tree = false), uuid))
      }
  }

  override def shutdown(): Future[Unit] = {
    stage(folder, WORKSPACE_TERMINATED)
    super.shutdown()
  }

  private def cleanFiles(closedFiles: List[(String, NotificationKind)]): Unit =
    closedFiles.foreach { cf =>
      repository.removeUnit(cf._1)
      subscribers.foreach(_.onRemoveFile(cf._1))
    }

  private def processChangeConfigChanges(snapshot: Snapshot): Future[Unit] = {
    changeState(ProcessingProject)
    stagingArea.enqueue(snapshot.files.filterNot(t => t._2 == CHANGE_CONFIG))
    workspaceConfigurationProvider match {
      case Some(cp) =>
        cp.obtainConfiguration(environmentProvider.platform, snapshot.environment)
          .flatMap(processChangeConfig)
      case _ => Future.failed(new Exception("Expected Configuration Provider"))
    }
  }

  private def processChangeConfig(maybeConfig: Option[WorkspaceConf]): Future[Unit] = {
    configMainFile = maybeConfig
    maybeConfig match {
      case Some(conf) =>
        repository.setCachables(conf.cachables)
        processMFChanges(conf.mainFile, stagingArea.snapshot())
      case _ =>
        repository.cleanTree()
        repository.setCachables(Set.empty)
        Future.unit
    }
  }

  private def processMFChanges(mainFile: String, snapshot: Snapshot): Future[Unit] = {
    changeState(ProcessingProject)
    val uuid = UUID.randomUUID().toString
    parse(s"$folder/$mainFile", snapshot.environment, uuid)
      .flatMap { u =>
        repository.newTree(u).map { _ =>
          subscribers.foreach(_.onNewAst(BaseUnitListenerParams(u, repository.references, tree = true), uuid))
          stagingArea.enqueue(snapshot.files.filter(t => !repository.inTree(t._1)))
        }
      }
  }

  private def parse(uri: String, environment: Environment, uuid: String): Future[AmfParseResult] = {
    telemetryProvider.addTimedMessage("Start AMF Parse",
                                      "WorkspaceContentManager",
                                      "parse",
                                      MessageTypes.BEGIN_PARSE,
                                      uri,
                                      uuid)
    environmentProvider.amfConfiguration.parserHelper
      .parse(FileUtils.getDecodedUri(uri, environmentProvider.platform),
             environment.withResolver(repository.resolverCache)) andThen {
      case _ =>
        telemetryProvider
          .addTimedMessage("End AMF Parse", "WorkspaceContentManager", "parse", MessageTypes.END_PARSE, uri, uuid)
    }
  }

  def getRelationships(uri: String): Relationships =
    Relationships(repository, () => Some(getCompilableUnit(uri)))

  override protected def log(msg: String): Unit =
    logger.error(msg, "WorkspaceContentManager", "Processing request")

  override protected def disableTasks(): Future[Unit] = Future {
    subscribers.map(d => repository.getAllFilesUris.foreach(d.onRemoveFile))
  }
}