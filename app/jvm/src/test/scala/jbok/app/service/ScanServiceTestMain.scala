package jbok.app.service

import jbok.JbokSpec
import jbok.app.service.store.ServiceStore
import jbok.common.execution._

class ScanServiceTestMain extends JbokSpec {
  val (store, _)   = ServiceStore.quill(None).allocated.unsafeRunSync()
  val service = new ScanService(store)
  service.serve(10087).compile.drain.unsafeRunSync()
}
