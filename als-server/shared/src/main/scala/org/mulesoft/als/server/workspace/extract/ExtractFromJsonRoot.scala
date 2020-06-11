package org.mulesoft.als.server.workspace.extract

import amf.core.remote.Platform
import amf.internal.environment.Environment
import org.mulesoft.als.common.FileUtils
import org.mulesoft.common.io.SyncFile
import org.yaml.model.{YDocument, YMap, YMapEntry}
import org.yaml.parser.JsonParser

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class ExtractFromJsonRoot(content: String) {

  val rootMap: Iterable[YMapEntry] = {
    JsonParser(content).parse(false).headOption match {
      case Some(d: YDocument) =>
        d.node.value match {
          case y: YMap => y.entries
          case _       => Nil
        }
      case _ => Nil
    }
  }

  lazy val getMain: Option[String] =
    rootMap.find(_.key.asScalar.exists(_.text == "main")).flatMap(_.value.asScalar.map(_.text))
}

object ExchangeConfigReader extends ConfigReader {
  override val configFileName: String = "exchange.json"

  override protected def buildConfig(content: String,
                                     path: String,
                                     platform: Platform): Option[Future[WorkspaceConf]] =
    new ExtractFromJsonRoot(content).getMain.map { m =>
      getSubList(platform.fs.syncFile(path), platform).map { dependencies =>
        // the encoding should be handled by each config reader plugin? or in general?
        // How to know if a config file already encoded the main file?
        WorkspaceConf(path, platform.encodeURI(m), dependencies, Some(this))
      }
    }

  private def getSubList(dir: SyncFile, platform: Platform): Future[Set[String]] =
    if (dir.list != null)
      findDependencies(dir.list.map(l => platform.fs.syncFile(dir.path + "/" + l)).filter(_.isDirectory), platform)
    else Future.successful(Set.empty)

  private def findDependencies(subDirs: Array[SyncFile],
                               platform: Platform,
                               environment: Environment = Environment()): Future[Set[String]] =
    if (subDirs.nonEmpty) {
      val (dependencies, others) = subDirs.partition(_.list.contains(configFileName))
      val mains: Future[Seq[String]] =
        Future.sequence {
          dependencies.map(
            d =>
              readFile(FileUtils.getEncodedUri(d.path + "/" + configFileName, platform), platform, environment)
                .map(_.flatMap { c =>
                  new ExtractFromJsonRoot(c).getMain.map(m => d.path + "/" + m)
                })) map (_.collect { case Some(c) => c })
        }

      mains.flatMap(
        innerMains =>
          findDependencies(
            others.flatMap(o => o.list.map(so => platform.fs.syncFile(o.path + "/" + so))).filter(_.isDirectory),
            platform,
            environment).map(_ ++ innerMains.toSet))
    } else Future.successful(Set.empty)
}