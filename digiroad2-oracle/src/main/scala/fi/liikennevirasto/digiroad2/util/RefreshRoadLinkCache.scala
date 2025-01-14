package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.client.Caching
import fi.liikennevirasto.digiroad2.client.RoadLinkClient
import fi.liikennevirasto.digiroad2.dao.Queries
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.{DummyEventBus, DummySerializer}
import org.slf4j.{Logger, LoggerFactory}



object RefreshRoadLinkCache {
  val logger: Logger = LoggerFactory.getLogger(getClass)
  lazy val roadLinkClient: RoadLinkClient = {
    new RoadLinkClient(Digiroad2Properties.vvhRestApiEndPoint)
  }

  lazy val roadLinkService: RoadLinkService = {
    new RoadLinkService(roadLinkClient, new DummyEventBus, new DummySerializer)
  }

  def refreshCache(): Unit = {
    if (Digiroad2Properties.caching){
      val municipalities: Seq[Int] = PostGISDatabase.withDynSession {
        Queries.getMunicipalities
      }
      val flushSuccess = LogUtils.time(logger, "Flushing cache"){
        Caching.flush()
      }

      if (flushSuccess) {
        municipalities.foreach(municipality => {
          roadLinkService.getRoadLinksAndComplementaryLinksByMunicipality(municipality)
        })
        logger.info("Cached roadlinks with overrided properties from database")
      }
      else logger.error("Flushing cache failed")
    }
    else logger.error("Caching disabled in properties, can't refresh cache")
  }
}
