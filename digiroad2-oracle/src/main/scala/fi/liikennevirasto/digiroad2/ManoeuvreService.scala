package fi.liikennevirasto.digiroad2

import com.github.tototoshi.slick.MySQLJodaSupport._
import fi.liikennevirasto.digiroad2.asset.Asset._
import fi.liikennevirasto.digiroad2.asset.BoundingRectangle
import fi.liikennevirasto.digiroad2.asset.oracle.Queries._
import fi.liikennevirasto.digiroad2.linearasset.VVHRoadLinkWithProperties
import fi.liikennevirasto.digiroad2.oracle.collections.OracleArray
import fi.liikennevirasto.digiroad2.oracle.{MassQuery, OracleDatabase}
import org.joda.time.DateTime
import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.StaticQuery.interpolation
import slick.jdbc.{StaticQuery => Q}

import scala.collection.JavaConversions._

case class Manoeuvre(id: Long, sourceMmlId: Long, destMmlId: Long, exceptions: Seq[Int], modifiedDateTime: String, modifiedBy: String, additionalInfo: String)
case class NewManoeuvre(exceptions: Seq[Int], additionalInfo: Option[String], sourceMmlId: Long, destMmlId: Long)
case class ManoeuvreUpdates(exceptions: Option[Seq[Int]], additionalInfo: Option[String])

class ManoeuvreService(roadLinkService: RoadLinkService) {

  val FirstElement = 1
  val LastElement = 3

  def getSourceRoadLinkMmlIdById(id: Long): Long = {
    OracleDatabase.withDynTransaction {
      sql"""
             select mml_id
             from manoeuvre_element
             where manoeuvre_id = $id and element_type = 1
          """.as[Long].first
    }
  }

  def getByMunicipality(municipalityNumber: Int): Seq[Manoeuvre] = {
    val roadLinks = roadLinkService.getRoadLinksFromVVH(municipalityNumber)
    getByMmlIds(roadLinks)
  }

  def getByBoundingBox(bounds: BoundingRectangle, municipalities: Set[Int]): Seq[Manoeuvre] = {
    val roadLinks = roadLinkService.getRoadLinksFromVVH(bounds, municipalities)
    getByMmlIds(roadLinks)
  }

  def deleteManoeuvre(username: String, id: Long) = {
    OracleDatabase.withDynTransaction {
      sqlu"""
             update manoeuvre
             set valid_to = sysdate, modified_date = sysdate, modified_by = $username
             where id = $id
          """.execute
    }
  }

  def createManoeuvre(userName: String, manoeuvre: NewManoeuvre): Long = {
    OracleDatabase.withDynTransaction {
      val manoeuvreId = sql"select manoeuvre_id_seq.nextval from dual".as[Long].first
      val additionalInfo = manoeuvre.additionalInfo.getOrElse("")
      sqlu"""
             insert into manoeuvre(id, type, modified_date, modified_by, additional_info)
             values ($manoeuvreId, 2, sysdate, $userName, $additionalInfo)
          """.execute

      val sourceRoadLinkId = 0
      val sourceMmlId = manoeuvre.sourceMmlId
      sqlu"""
             insert into manoeuvre_element(manoeuvre_id, road_link_id, element_type, mml_id)
             values ($manoeuvreId, $sourceRoadLinkId, $FirstElement, $sourceMmlId)
          """.execute

      val destRoadLinkId = 0
      val destMmlId = manoeuvre.destMmlId
      sqlu"""
             insert into manoeuvre_element(manoeuvre_id, road_link_id, element_type, mml_id)
             values ($manoeuvreId, $destRoadLinkId, $LastElement, $destMmlId)
          """.execute

      addManoeuvreExceptions(manoeuvreId, manoeuvre.exceptions)
      manoeuvreId
    }
  }

  def updateManoeuvre(userName: String, manoeuvreId: Long, manoeuvreUpdates: ManoeuvreUpdates) = {
    OracleDatabase.withDynTransaction {
      manoeuvreUpdates.additionalInfo.map(setManoeuvreAdditionalInfo(manoeuvreId))
      manoeuvreUpdates.exceptions.map(setManoeuvreExceptions(manoeuvreId))
      updateModifiedData(userName, manoeuvreId)
    }
  }

  private def addManoeuvreExceptions(manoeuvreId: Long, exceptions: Seq[Int]) {
    if (exceptions.nonEmpty) {
      val query = s"insert all " +
        exceptions.map { exception => s"into manoeuvre_exceptions (manoeuvre_id, exception_type) values ($manoeuvreId, $exception) "}.mkString +
        s"select * from dual"
      Q.updateNA(query).execute
    }
  }

  private def getByMmlIds(roadLinks: Seq[VVHRoadLinkWithProperties]): Seq[Manoeuvre] = {
    OracleDatabase.withDynTransaction {
      val manoeuvresById = fetchManoeuvresByMmlIds(roadLinks.map(_.mmlId))
      val manoeuvreExceptionsById = fetchManoeuvreExceptionsByIds(manoeuvresById.keys.toSeq)

      manoeuvresById.filter { case (id, links) =>
        links.size == 2 && links.exists(_._4 == FirstElement) && links.exists(_._4 == LastElement)
      }.map { case (id, links) =>
        val (_, _, sourceMmlId, _, modifiedDate, modifiedBy, additionalInfo) = links.find(_._4 == FirstElement).get
        val (_, _, destMmlId, _, _, _, _) = links.find(_._4 == LastElement).get
        val modifiedTimeStamp = DateTimePropertyFormat.print(modifiedDate)

        Manoeuvre(id, sourceMmlId, destMmlId, manoeuvreExceptionsById.getOrElse(id, Seq()), modifiedTimeStamp, modifiedBy, additionalInfo)
      }.filter { isAdjacent(_, roadLinks) }.toSeq
    }
  }

  private def isAdjacent(manoeuvre: Manoeuvre, roadLinks: Seq[VVHRoadLinkWithProperties]): Boolean = {
    val sourceEndPoints = roadLinks.find(_.mmlId == manoeuvre.sourceMmlId)
      .map(link => GeometryUtils.geometryEndpoints(link.geometry))

    val adjacentLinks = sourceEndPoints.map { endpoint =>
      roadLinks.filterNot(_.mmlId == manoeuvre.sourceMmlId)
        .filter(roadLink => roadLink.isCarTrafficRoad)
        .filter { roadLink =>
        val geometry = roadLink.geometry
        val epsilon = 0.01
        val rlEndpoints = GeometryUtils.geometryEndpoints(geometry)
        rlEndpoints._1.distanceTo(endpoint._1) < epsilon ||
          rlEndpoints._2.distanceTo(endpoint._1) < epsilon ||
          rlEndpoints._1.distanceTo(endpoint._2) < epsilon ||
          rlEndpoints._2.distanceTo(endpoint._2) < epsilon
      }
    }.getOrElse(Nil)

    adjacentLinks.exists(_.mmlId == manoeuvre.destMmlId)
  }

  private def fetchManoeuvresByMmlIds(mmlIds: Seq[Long]): Map[Long, Seq[(Long, Int, Long, Int, DateTime, String, String)]] = {
    val manoeuvres = MassQuery.withIds(mmlIds.toSet) { idTableName =>
      sql"""SELECT m.id, m.type, e.mml_id, e.element_type, m.modified_date, m.modified_by, m.additional_info
            FROM MANOEUVRE m
            JOIN MANOEUVRE_ELEMENT e ON m.id = e.manoeuvre_id
            WHERE m.id in (SELECT distinct(k.manoeuvre_id)
                            FROM MANOEUVRE_ELEMENT k
                            join #$idTableName i on i.id = k.mml_id
                            where valid_to is null)""".as[(Long, Int, Long, Int, DateTime, String, String)].list
    }
    manoeuvres.groupBy(_._1)
  }

  private def fetchManoeuvreExceptionsByIds(manoeuvreIds: Seq[Long]): Map[Long, Seq[Int]] = {
    val manoeuvreExceptions = OracleArray.fetchManoeuvreExceptionsByIds(manoeuvreIds, bonecpToInternalConnection(dynamicSession.conn))
    val manoeuvreExceptionsById: Map[Long, Seq[Int]] = manoeuvreExceptions.toList.groupBy(_._1).mapValues(_.map(_._2))
    manoeuvreExceptionsById
  }

  private def setManoeuvreExceptions(manoeuvreId: Long)(exceptions: Seq[Int]) = {
    sqlu"""
           delete from manoeuvre_exceptions where manoeuvre_id = $manoeuvreId
        """.execute
    addManoeuvreExceptions(manoeuvreId, exceptions)
  }

  private def updateModifiedData(username: String, manoeuvreId: Long) {
    sqlu"""
           update manoeuvre
           set modified_date = sysdate, modified_by = $username
           where id = $manoeuvreId
        """.execute
  }

  private def setManoeuvreAdditionalInfo(manoeuvreId: Long)(additionalInfo: String) = {
    sqlu"""
           update manoeuvre
           set additional_info = $additionalInfo
           where id = $manoeuvreId
        """.execute
  }
}
