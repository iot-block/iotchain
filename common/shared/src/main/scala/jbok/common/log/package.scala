package jbok.common

package object log {
  def getLogger(name: String): scribe.Logger = scribe.Logger(name)
}
