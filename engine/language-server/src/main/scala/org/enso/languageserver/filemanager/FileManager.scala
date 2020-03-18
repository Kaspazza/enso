package org.enso.languageserver.filemanager

import akka.actor.{Actor, Props}
import akka.routing.SmallestMailboxPool
import akka.pattern.pipe
import org.enso.languageserver.ZioExec
import org.enso.languageserver.data.Config
import zio._
import zio.blocking.blocking

class FileManager(config: Config, fs: FileSystem) extends Actor {

  import context.dispatcher

  override def receive: Receive = {
    case FileManagerProtocol.WriteFile(path, content) =>
      val result =
        for {
          rootPath <- IO.fromEither(config.findContentRoot(path.rootId))
          _        <- fs.write(path.toFile(rootPath), content)
        } yield ()
      ZioExec()
        .execTimed(config.fileManager.timeout, blocking(result))
        .map(FileManagerProtocol.WriteFileResult)
        .pipeTo(sender())
      ()

    case FileManagerProtocol.ReadFile(path) =>
      val result =
        for {
          rootPath <- IO.fromEither(config.findContentRoot(path.rootId))
          content  <- fs.read(path.toFile(rootPath))
        } yield content
      ZioExec()
        .execTimed(config.fileManager.timeout, blocking(result))
        .map(FileManagerProtocol.ReadFileResult)
        .pipeTo(sender())
      ()

    case FileManagerProtocol.CreateFile(FileSystemObject.File(name, path)) =>
      val result =
        for {
          rootPath <- IO.fromEither(config.findContentRoot(path.rootId))
          _        <- fs.createFile(path.toFile(rootPath, name))
        } yield ()
      ZioExec()
        .execTimed(config.fileManager.timeout, blocking(result))
        .map(FileManagerProtocol.CreateFileResult)
        .pipeTo(sender())
      ()

    case FileManagerProtocol.CreateFile(
        FileSystemObject.Directory(name, path)
        ) =>
      val result =
        for {
          rootPath <- IO.fromEither(config.findContentRoot(path.rootId))
          _        <- fs.createDirectory(path.toFile(rootPath, name))
        } yield ()
      ZioExec()
        .execTimed(config.fileManager.timeout, blocking(result))
        .map(FileManagerProtocol.CreateFileResult)
        .pipeTo(sender())
      ()

    case FileManagerProtocol.DeleteFile(path) =>
      val result =
        for {
          rootPath <- IO.fromEither(config.findContentRoot(path.rootId))
          _        <- fs.delete(path.toFile(rootPath))
        } yield ()
      ZioExec()
        .execTimed(config.fileManager.timeout, blocking(result))
        .map(FileManagerProtocol.DeleteFileResult)
        .pipeTo(sender())
      ()

    case FileManagerProtocol.CopyFile(from, to) =>
      val result =
        for {
          rootPathFrom <- IO.fromEither(config.findContentRoot(from.rootId))
          rootPathTo   <- IO.fromEither(config.findContentRoot(to.rootId))
          _            <- fs.copy(from.toFile(rootPathFrom), to.toFile(rootPathTo))
        } yield ()
      ZioExec()
        .execTimed(config.fileManager.timeout, blocking(result))
        .map(FileManagerProtocol.CopyFileResult)
        .pipeTo(sender())
      ()

    case FileManagerProtocol.MoveFile(from, to) =>
      val result =
        for {
          rootPathFrom <- IO.fromEither(config.findContentRoot(from.rootId))
          rootPathTo   <- IO.fromEither(config.findContentRoot(to.rootId))
          _            <- fs.move(from.toFile(rootPathFrom), to.toFile(rootPathTo))
        } yield ()
      ZioExec()
        .execTimed(config.fileManager.timeout, blocking(result))
        .map(FileManagerProtocol.MoveFileResult)
        .pipeTo(sender())
      ()

    case FileManagerProtocol.ExistsFile(path) =>
      val result =
        for {
          rootPath <- IO.fromEither(config.findContentRoot(path.rootId))
          exists   <- fs.exists(path.toFile(rootPath))
        } yield exists
      ZioExec()
        .execTimed(config.fileManager.timeout, blocking(result))
        .map(FileManagerProtocol.ExistsFileResult)
        .pipeTo(sender())
      ()

    case FileManagerProtocol.TreeFile(path, depth) =>
      val result =
        for {
          rootPath  <- IO.fromEither(config.findContentRoot(path.rootId))
          directory <- fs.tree(path.toFile(rootPath), depth)
        } yield DirectoryTree.fromDirectoryEntry(rootPath, path, directory)
      ZioExec()
        .execTimed(config.fileManager.timeout, blocking(result))
        .map(FileManagerProtocol.TreeFileResult)
        .pipeTo(sender())
      ()
  }
}

object FileManager {

  def props(config: Config, fs: FileSystem): Props =
    SmallestMailboxPool(config.fileManager.parallelism)
      .props(Props(new FileManager(config, fs)))

}
