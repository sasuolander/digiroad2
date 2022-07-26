package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.asset.{CycleOrPedestrianPath, LinkType, MotorwayServiceAccess, SpecialTransportWithGate, SpecialTransportWithoutGate, TractorRoad, TrafficDirection}
import fi.liikennevirasto.digiroad2.asset.TrafficDirection.toSideCode
import fi.liikennevirasto.digiroad2.client.viite.SearchViiteClient
import fi.liikennevirasto.digiroad2.{DummyEventBus, DummySerializer, lane}
import fi.liikennevirasto.digiroad2.client.vvh.VVHClient
import fi.liikennevirasto.digiroad2.dao.Queries
import fi.liikennevirasto.digiroad2.lane.{LaneProperty, LanePropertyValue, LaneType, PersistedLane}
import fi.liikennevirasto.digiroad2.linearasset.RoadLink
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import fi.liikennevirasto.digiroad2.service.{RoadAddressService, RoadLinkService}
import fi.liikennevirasto.digiroad2.service.lane.LaneService
import org.apache.http.impl.client.HttpClientBuilder
import org.joda.time.DateTime
import org.slf4j.LoggerFactory

import java.text.SimpleDateFormat

object MainLanePopulationProcess {

  lazy val vvhClient: VVHClient = {
    new VVHClient(Digiroad2Properties.vvhRestApiEndPoint)
  }

  lazy val roadLinkService: RoadLinkService = {
    new RoadLinkService(vvhClient, new DummyEventBus, new DummySerializer)
  }

  lazy val roadAddressService: RoadAddressService = {
    val viiteClient = new SearchViiteClient(Digiroad2Properties.viiteRestApiEndPoint, HttpClientBuilder.create().build())
    new RoadAddressService(viiteClient)
  }

  lazy val laneService: LaneService = {
    new LaneService(roadLinkService, new DummyEventBus, roadAddressService)
  }

  lazy val username = "auto_generated_lane"

  lazy val twoWayLanes: Seq[LinkType] = Seq(
      SpecialTransportWithoutGate, SpecialTransportWithGate, MotorwayServiceAccess,
      TractorRoad, CycleOrPedestrianPath)

  private val logger = LoggerFactory.getLogger(getClass)

  private def addMainLane(roadLink: RoadLink): PersistedLane = {
    val createdVVHTimeStamp = vvhClient.createVVHTimeStamp()
    val sideCode = toSideCode(roadLink.trafficDirection).value
    val laneCode = 1
    val startMeasure = 0.0
    // Use three decimals
    val endMeasure = Math.round(roadLink.length * 1000).toDouble / 1000
    val dateFormat = new SimpleDateFormat("dd.M.yyyy")
    val startDate = dateFormat.format(DateTime.now().toDate)

    val laneProperties = Seq(
      LaneProperty("lane_code", Seq(LanePropertyValue(laneCode))),
      LaneProperty("lane_type", Seq(LanePropertyValue(LaneType.Main.value))),
      LaneProperty("start_date", Seq(LanePropertyValue(startDate)))
    )

    PersistedLane(0, roadLink.linkId, sideCode, laneCode, roadLink.municipalityCode, startMeasure, endMeasure,
      Some(username), Some(DateTime.now()), None, None, None, None, expired = false,
      createdVVHTimeStamp, None, laneProperties)
  }

  // Split road links by traffic direction
  // Two way lanes allowed only for links that are service openings, cycle or pedestrian path, or tractor road
  private def splitLinksByTrafficDirection(roadLink: RoadLink): Seq[RoadLink] = {
    val twoWayLane = twoWayLanes.contains(roadLink.linkType)
    roadLink.trafficDirection match {
      case TrafficDirection.BothDirections if !twoWayLane =>
        Seq(roadLink.copy(trafficDirection = TrafficDirection.TowardsDigitizing),
            roadLink.copy(trafficDirection = TrafficDirection.AgainstDigitizing))
      case _ if twoWayLane =>
        Seq(roadLink.copy(trafficDirection = TrafficDirection.BothDirections))
      case _ =>
        Seq(roadLink)
    }
  }

  private def mainLanesForMunicipality(municipality: Int, initialProcessing: Boolean): Unit = {
    logger.info("Working on municipality -> " + municipality)

    val roadLinks = roadLinkService.getRoadLinksFromVVHByMunicipality(municipality)

    // If not initial process, filter out roadLinks that already have main lanes
    val roadLinksWithoutMainLanes =
      if (initialProcessing) roadLinks
      else {
        val existingLanes = laneService.fetchExistingMainLanesByRoadLinks(roadLinks, Seq()).groupBy(_.linkId)
        roadLinks.filterNot(roadLink => existingLanes.exists(_._1 == roadLink.linkId))
      }

    logger.info(roadLinksWithoutMainLanes.length + " road links without main lanes.")

    val municipalityMainLanes = roadLinksWithoutMainLanes.flatMap { roadLink =>
      splitLinksByTrafficDirection(roadLink).map { linkWithUpdatedDirection =>
        addMainLane(linkWithUpdatedDirection)
      }
    }

    if (municipalityMainLanes.nonEmpty) {
      val lanes = laneService.createMultipleLanes(municipalityMainLanes, username, newTransaction = true)
      logger.info(s"${lanes.size} lanes created for ${roadLinksWithoutMainLanes.size} links")
    }
  }

  // Main process
  def process(initialProcessing: Boolean = false): Unit = {
    logger.info(s"Start to populate main lanes from road links ${DateTime.now()}")

    val municipalities: Seq[Int] = PostGISDatabase.withDynSession {
      Queries.getMunicipalities
    }

    municipalities.foreach { municipality =>
      mainLanesForMunicipality(municipality, initialProcessing)
    }

    logger.info(s"Finished populating main lanes ${DateTime.now()}")
  }

  // Moves all existing lanes to history and creates new main lanes from vvh road links
  def initialProcess(): Unit = {
    logger.info(s"Truncate all lane tables ${DateTime.now()}")

    PostGISDatabase.withDynTransaction(laneService.deleteAllPreviousLaneData())

    logger.info(s"Finished removing all lane data ${DateTime.now()}")

    // Populate main lanes from road links
    process(initialProcessing = true)
  }
}