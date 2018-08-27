package jbok

import org.scalatest.prop.PropertyChecks
import org.scalatest._

abstract class JbokSpec
    extends WordSpec
    with Matchers
    with PropertyChecks
    with BeforeAndAfterAll
    with BeforeAndAfterEach

abstract class JbokAsyncSpec extends AsyncWordSpec with Matchers
