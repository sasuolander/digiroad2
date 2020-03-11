package fi.liikennevirasto.digiroad2.csvDataImporter

import fi.liikennevirasto.digiroad2.asset.State
import fi.liikennevirasto.digiroad2.client.vvh.VVHClient
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.{DigiroadEventBus, GeometryUtils}
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.service.pointasset.{IncomingTrafficLight, TrafficLightService}
import fi.liikennevirasto.digiroad2.user.User

class TrafficLightsCsvImporter(roadLinkServiceImpl: RoadLinkService, eventBusImpl: DigiroadEventBus) extends PointAssetCsvImporter {
  override def withDynTransaction[T](f: => T): T = OracleDatabase.withDynTransaction(f)
  override def withDynSession[T](f: => T): T = OracleDatabase.withDynSession(f)
  override def roadLinkService: RoadLinkService = roadLinkServiceImpl
  override def vvhClient: VVHClient = roadLinkServiceImpl.vvhClient
  override def eventBus: DigiroadEventBus = eventBusImpl

  override val mandatoryFieldsMapping = coordinateMappings

  lazy val trafficLightsService: TrafficLightService = new TrafficLightService(roadLinkService)

  override def createAsset(pointAssetAttributes: Seq[CsvAssetRowAndRoadLink], user: User, result: ImportResultPointAsset): ImportResultPointAsset = {
    pointAssetAttributes.foreach { trafficLightAttribute =>
      val csvProperties = trafficLightAttribute.properties
      val nearbyLinks = trafficLightAttribute.roadLink

      val position = getCoordinatesFromProperties(csvProperties)

      val roadLink = roadLinkService.enrichRoadLinksFromVVH(nearbyLinks)
      val nearestRoadLink = roadLink.filter(_.administrativeClass != State).minBy(r => GeometryUtils.minimumDistance(position, r.geometry))

      val floating = checkMinimumDistanceFromRoadLink(position, nearestRoadLink.geometry)

      trafficLightsService.createFromCoordinates(IncomingTrafficLight(position.x, position.y, nearestRoadLink.linkId, Set()), nearestRoadLink, user.username, floating)
    }
    result
  }
}