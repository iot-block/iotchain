package jbok.common

import com.typesafe.scalalogging.StrictLogging

trait Logging extends StrictLogging {
  @inline def log = logger
}
