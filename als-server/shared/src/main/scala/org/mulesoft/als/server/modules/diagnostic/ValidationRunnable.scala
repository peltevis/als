package org.mulesoft.als.server.modules.diagnostic

import org.mulesoft.als.server.modules.common.reconciler.Runnable

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Success}

class ValidationRunnable(var uri: String, task: () => Future[Unit]) extends Runnable[Unit] {
  private var canceled = false

  private val kind = "ValidationRunnable"

  def run(): Promise[Unit] = {
    val promise = Promise[Unit]()

    task() andThen {
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
