package jbok.app.service.store.doobie

import doobie._
import io.circe.generic.extras.Configuration.snakeCaseTransformation
import scala.language.experimental.macros
import magnolia._

/** Table: generic helper for getting field-names and insert statements.
  *
  * Doobie doesn't do anything to help creating INSERT and UPDATE
  * statements. This fills that gap using shapeless.
  *
  * First create an implicit instance for the case-class corresponding
  * to your database table:
  *
  *     implicit val myThingTable: Table[MyThing] =
  *         Table.forType[MyThing].create()
  *
  * You can now use that for inserts:
  *
  *     myThingTable.insert.run(myThingInst)
  *
  * Or for selects:
  *
  *     (sql"SELECT" ++
  *      myThingTable.selectFields("thingA") ++
  *      myThingTable.selectFields("thingB") ++
  *      sql"FROM my_thing AS a, my_thing AS b WHERE ...")
  *     .query[(MyThing, MyThing)].to[Vector]
  */
trait Table[A] {
  def fieldNames: Vector[String]

  def tableName: String

  def insert(data: A)(implicit writer: Write[A]): Update0 = {
    val holes = Vector.fill(fieldNames.size)("?").mkString(",")

    val quotedFieldNames = fieldNames.map('"'.toString + _ + '"'.toString).mkString(",")

    Update(s"""INSERT INTO "$tableName" ($quotedFieldNames) VALUES ($holes)""")
      .toUpdate0(data)
  }

  def selectFields(table: String): Fragment =
    Fragment.const0(fieldNames.map(f => s""""$table"."$f" AS "$table.$f"""").mkString(", "))
}

object Table {
  type Typeclass[A] = Table[A]

  def combine[A](ctx: CaseClass[Table, A]): Table[A] = {
    val table = new Table[A] {
      override val fieldNames: Vector[String] = ctx.parameters.map(_.label).toVector
      override val tableName: String          = ctx.typeName.short
    }
    table
  }

  implicit def gen[A]: Table[A] = macro Magnolia.gen[A]
}
