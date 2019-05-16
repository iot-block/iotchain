package jbok.app.service

import jbok.common.CommonSpec

class ScanServiceTestMain extends CommonSpec {
  val file = "test_jbok.db"

  "ScanServiceTestMain" ignore {
//    implicit val scheduler             = Scheduler.global
//    implicit val options: Task.Options = Task.defaultOptions
//    implicit val taskEff               = new CatsConcurrentEffectForTask
//    val s = for {
//      _     <- Stream.eval(Migration.migrate(file))
//      store <- Stream.resource(ServiceStore.quill(file))
//      service = new ScanService(store, ServiceConfig())
//      ec <- service.serve
//    } yield ec
//
//    s.compile.drain.unsafeRunSync()
  }
}
