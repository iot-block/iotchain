package jbok

import org.scalatest.prop.PropertyChecks
import org.scalatest.{AsyncWordSpec, BeforeAndAfterAll, Matchers, WordSpec}

abstract class JbokSpec extends WordSpec with Matchers with PropertyChecks with BeforeAndAfterAll

abstract class JbokAsyncSpec extends AsyncWordSpec with Matchers
