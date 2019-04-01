package jbok.app.service.middleware

import cats.effect.IO
import org.http4s.rho.RhoMiddleware
import org.http4s.rho.swagger.syntax.{io => ioSwagger}
import org.http4s.rho.swagger.models._
import org.http4s.rho.swagger._
import cats.implicits._

object Swagger {
  val title       = "JBOK SCAN API"
  val version     = "1.0.0"
  val description = Some("JBOK SCAN API")
  val apiInfo     = Info(title, version, description)
  val classModel: Set[Model] = Set(
    ModelImpl(
      id = "OhoResp",
      id2 = "OhoResp",
      description = "MyClass".some,
      name = "MyClass".some,
      properties = Map(
        "code" -> StringProperty(
          required = true,
          description = "name of MyClass".some,
          enums = Set()
        ),
        "category" -> StringProperty(
          required = true,
          description = "enum of category".some,
          enums = Set("A", "B", "C")
        )
      ),
      `type` = "object".some
    )
  )

  def swaggerMiddleware(basePath: String): RhoMiddleware[IO] = ioSwagger.createRhoMiddleware(
    swaggerFormats = DefaultSwaggerFormats,
    apiInfo = apiInfo,
    basePath = Some(basePath),
    schemes = List(Scheme.HTTP, Scheme.HTTPS),
    security = List(SecurityRequirement("bearer", List())),
    securityDefinitions = Map(
      "bearer" -> ApiKeyAuthDefinition("Authorization", In.HEADER),
    )
  )
}
