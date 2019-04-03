package jbok.app.service

import better.files.File
import fs2._
import jbok.JbokSpec
import jbok.app.service.store.{Migration, ServiceStore}
import jbok.common.execution._
import monix.eval.Task
import monix.eval.instances.CatsConcurrentEffectForTask
import monix.execution.Scheduler

class ScanServiceTestMain extends JbokSpec {
  val file = "test_mly.db"

  "ScanServiceTestMain" should {
    implicit val scheduler             = Scheduler.global
    implicit val options: Task.Options = Task.defaultOptions
    implicit val taskEff               = new CatsConcurrentEffectForTask
    val s = for {
      _     <- Stream.eval(Migration.migrate(Some(file)))
      store <- Stream.resource(ServiceStore.quill(Some(file)))
      service = new ScanService(store)
      ec <- service.serve(10087)
    } yield ec

    s.compile.drain.unsafeRunSync()
  }
}
