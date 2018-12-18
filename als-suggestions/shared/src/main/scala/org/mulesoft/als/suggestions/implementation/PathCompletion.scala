package org.mulesoft.als.suggestions.implementation

import org.mulesoft.high.level.interfaces.IFSProvider

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

object PathCompletion {

  def complete(defaultPath: String, additionalPath: String,
               contentProvider: IFSProvider): Future[Seq[String]] =  {

    contentProvider.isDirectoryAsync(defaultPath).flatMap(isDir=>{
    var directoryPath =
      if (!isDir)
        contentProvider.dirName(defaultPath)
      else
        defaultPath

    if (!directoryPath.endsWith("/")) directoryPath = directoryPath + "/"

    val additionalDirectoryPath: Option[String] =
      if (additionalPath.contains('/'))
        Some(additionalPath.substring(0, additionalPath.lastIndexOf('/') + 1))
      else
        None

    val modifiedDirectoryPath: String =
      if (additionalDirectoryPath.isDefined) {

        val resolved = contentProvider.resolve(directoryPath, additionalDirectoryPath.get)

        if (resolved.isDefined) resolved.get else directoryPath
      }
      else {
        directoryPath
      }

    var finalDirectoryPath = directoryPath

    val filesFuture = contentProvider.isDirectoryAsync(modifiedDirectoryPath)
      .flatMap(modifiedIsDirectory=>{

        if (modifiedIsDirectory) {
          finalDirectoryPath = modifiedDirectoryPath
        }

        contentProvider.readDirAsync(finalDirectoryPath)
    })

    filesFuture.flatMap(shortFilePaths=>{

      val fullFilePaths: Seq[String] =
        shortFilePaths.filter(

          shortFilePath=>contentProvider.resolve(finalDirectoryPath, shortFilePath).isDefined
        ).map(

          shortFilePath=>contentProvider.resolve(finalDirectoryPath, shortFilePath).get
        )

      val filter = contentProvider.resolve(directoryPath, additionalPath)

      val futures:Seq[Future[String]] = fullFilePaths.filter(fullFilePath=> filter.isDefined && fullFilePath.startsWith(filter.get) && fullFilePath != defaultPath)
          .map(fullFilePath=>{
              var result = fullFilePath.substring(directoryPath.length)
              contentProvider.isDirectoryAsync(fullFilePath).map({
                  case true => result + "/"
                  case false => result
              })
          })
       Future.sequence(futures)
    })
    })
  }
}