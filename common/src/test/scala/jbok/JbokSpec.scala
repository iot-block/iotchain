package jbok

import org.scalatest.prop.PropertyChecks
import org.scalatest.{Matchers, WordSpec}

abstract class JbokSpec extends WordSpec with Matchers with PropertyChecks
