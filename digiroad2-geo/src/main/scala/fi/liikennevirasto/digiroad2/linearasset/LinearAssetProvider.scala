package fi.liikennevirasto.digiroad2.linearasset

import fi.liikennevirasto.digiroad2.Point
import fi.liikennevirasto.digiroad2.asset.BoundingRectangle
import fi.liikennevirasto.digiroad2.asset.AdministrativeClass

case class SpeedLimitLink(id: Long, roadLinkId: Long, sideCode: Int, value: Option[Int], points: Seq[Point], position: Int, towardsLinkChain: Boolean)
case class SpeedLimit(id: Long, value: Option[Int], endpoints: Set[Point], modifiedBy: Option[String], modifiedDateTime: Option[String], createdBy: Option[String], createdDateTime: Option[String], speedLimitLinks: Seq[SpeedLimitLink])
case class RoadLinkForSpeedLimit(geometry: Seq[Point], length: Double, administrativeClass: AdministrativeClass, mmlId: Long)
case class SpeedLimitDTO(assetId: Long, mmlId: Long, sideCode: Int, value: Option[Int], geometry: Seq[Point])

trait LinearAssetProvider {
  def updateSpeedLimitValue(id: Long, value: Int, username: String, municipalityValidation: Int => Unit): Option[Long]
  def updateSpeedLimitValues(ids: Seq[Long], value: Int, username: String, municipalityValidation: Int => Unit): Seq[Long]
  def splitSpeedLimit(id: Long, mmlId: Long, splitMeasure: Double, value: Int, username: String, municipalityValidation: Int => Unit): Seq[SpeedLimit]
  def getSpeedLimits(bounds: BoundingRectangle, municipalities: Set[Int]): Seq[SpeedLimitLink]
  def getSpeedLimit(segmentId: Long): Option[SpeedLimit]
  def fillPartiallyFilledRoadLinks(linkGeometries: Map[Long, RoadLinkForSpeedLimit]): Unit
  def markSpeedLimitsFloating(ids: Set[Long]): Unit
  def getSpeedLimits(municipality: Int): Seq[Map[String, Any]]
}