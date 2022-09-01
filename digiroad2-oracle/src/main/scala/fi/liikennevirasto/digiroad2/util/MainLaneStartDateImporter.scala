package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.asset.AutoGeneratedValues
import fi.liikennevirasto.digiroad2.client.RoadLinkClient
import fi.liikennevirasto.digiroad2.csvDataImporter.LanesCsvImporter
import fi.liikennevirasto.digiroad2.middleware.UpdateOnlyStartDates
import fi.liikennevirasto.digiroad2.service.{AwsService, RoadLinkService}
import fi.liikennevirasto.digiroad2.user.{Configuration, User}
import fi.liikennevirasto.digiroad2.{DummyEventBus, DummySerializer}
import org.slf4j.LoggerFactory

object MainLaneStartDateImporter {
  lazy val roadLinkClient = new RoadLinkClient(Digiroad2Properties.vvhRestApiEndPoint)
  lazy val roadLinkService = new RoadLinkService(roadLinkClient, new DummyEventBus, new DummySerializer)
  lazy val lanesCsvImporter: LanesCsvImporter = new LanesCsvImporter(roadLinkService, new DummyEventBus)
  val logger = LoggerFactory.getLogger(getClass)
  val awsService = new AwsService

  def main(args: Array[String]): Unit = {

    val s3bucket = args(0)
    val objectKey = args(1)
    processStartDates(s3bucket, objectKey)

    def processStartDates(s3bucket: String, objectKey: String) = {
      val user = User(0, AutoGeneratedValues.startDateImporter.toString, Configuration())
      val onlyStartDates = UpdateOnlyStartDates(true)

      val s3Object = awsService.S3.getObjectFromS3(s3bucket, objectKey)
      val fileName = objectKey
      val result = lanesCsvImporter.processing(s3Object, user, onlyStartDates, fileName)

      logger.info("Not imported data: " + result.notImportedData.map(row => row.reason + " on row: " + row.csvRow))
      logger.info("Excluded rows: " + result.excludedRows)
      logger.info("Malformed rows: "+ result.malformedRows)
      logger.info("Incomplete rows: " + result.incompleteRows)
    }
  }
}
