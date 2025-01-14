package fi.liikennevirasto.digiroad2.service.linearasset

import fi.liikennevirasto.digiroad2.{DigiroadEventBus, Point}
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.{FeatureClass, RoadLinkFetched}
import fi.liikennevirasto.digiroad2.dao.linearasset.PostGISLinearAssetDao
import fi.liikennevirasto.digiroad2.linearasset.{NumericValue, PersistedLinearAsset, RoadLink}
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.util.{LinkIdGenerator, PolygonTools, TestTransactions}
import org.joda.time.DateTime
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{verify, when}
import org.scalatest.{FunSuite, Matchers}
import org.scalatest.mockito.MockitoSugar

class NumericValueLinearAssetServiceSpec extends FunSuite with Matchers {
  val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
  val mockPolygonTools = MockitoSugar.mock[PolygonTools]

  val linkId = LinkIdGenerator.generateRandom()
  
  when(mockRoadLinkService.fetchByLinkId(linkId)).thenReturn(Some(RoadLinkFetched(linkId, 235, Seq(Point(0, 0), Point(10, 0)), Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)))
  when(mockRoadLinkService.fetchRoadlinksByIds(any[Set[String]])).thenReturn(Seq(RoadLinkFetched(linkId, 235, Seq(Point(0, 0), Point(10, 0)), Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)))
  when(mockRoadLinkService.fetchNormalOrComplimentaryRoadLinkByLinkId(any[String])).thenReturn(Some(RoadLinkFetched(linkId, 235, Seq(Point(0, 0), Point(10, 0)), Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)))

  val roadLinkWithLinkSource = RoadLink(
    "1", Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, Municipality,
    1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235), "SURFACETYPE" -> BigInt(2)), ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)
  when(mockRoadLinkService.getRoadLinksAndChanges(any[BoundingRectangle], any[Set[Int]],any[Boolean])).thenReturn((List(roadLinkWithLinkSource), Nil))
  when(mockRoadLinkService.getRoadLinksWithComplementaryAndChanges(any[Int])).thenReturn((List(roadLinkWithLinkSource), Nil))
  when(mockRoadLinkService.getRoadLinksAndComplementariesByLinkIds(any[Set[String]], any[Boolean])).thenReturn(Seq(roadLinkWithLinkSource))

  val mockLinearAssetDao = MockitoSugar.mock[PostGISLinearAssetDao]
  when(mockLinearAssetDao.fetchLinearAssetsByLinkIds(30, Seq("1"), "mittarajoitus", false))
    .thenReturn(Seq(PersistedLinearAsset(1, "1", 1, Some(NumericValue(40000)), 0.4, 9.6, None, None, None, None, false, 30, 0, None, LinkGeomSource.NormalLinkInterface, None, None, None)))
  val mockEventBus = MockitoSugar.mock[DigiroadEventBus]
  val linearAssetDao = new PostGISLinearAssetDao()

  object ServiceWithDao extends NumericValueLinearAssetService(mockRoadLinkService, mockEventBus) {
    override def withDynTransaction[T](f: => T): T = f
    override def roadLinkService: RoadLinkService = mockRoadLinkService
    override def dao: PostGISLinearAssetDao = linearAssetDao
    override def eventBus: DigiroadEventBus = mockEventBus
    override def polygonTools: PolygonTools = mockPolygonTools
    override def createTimeStamp(offsetHours:Int=5): Long = 0L 
    override def getUncheckedLinearAssets(areas: Option[Set[Int]]) = throw new UnsupportedOperationException("Not supported method")
  }

  def runWithRollback(test: => Unit): Unit = TestTransactions.runWithRollback(PostGISDatabase.ds)(test)

  test("Expire numerical limit") {
    runWithRollback {
      ServiceWithDao.expire(Seq(11111l), "lol")
      val limit = linearAssetDao.fetchLinearAssetsByIds(Set(11111), "mittarajoitus").head
      limit.expired should be (true)
    }
  }

  test("Update numerical limit") {
    runWithRollback {
      //Update Numeric Values By Expiring the Old Asset
      val limitToUpdate = linearAssetDao.fetchLinearAssetsByIds(Set(11111), "mittarajoitus").head
      val newAssetIdCreatedWithUpdate = ServiceWithDao.update(Seq(11111l), NumericValue(2000), "UnitTestsUser")

      //Verify if the new data of the new asset is equal to old asset
      val limitUpdated = linearAssetDao.fetchLinearAssetsByIds(newAssetIdCreatedWithUpdate.toSet, "mittarajoitus").head

      limitUpdated.id should not be (limitToUpdate.id)
      limitUpdated.linkId should be (limitToUpdate.linkId)
      limitUpdated.sideCode should be (limitToUpdate.sideCode)
      limitUpdated.value should be (Some(NumericValue(2000)))
      limitUpdated.startMeasure should be (limitToUpdate.startMeasure)
      limitUpdated.endMeasure should be (limitToUpdate.endMeasure)
      limitUpdated.createdBy should be (limitToUpdate.createdBy)
      limitUpdated.createdDateTime should be (limitToUpdate.createdDateTime)
      limitUpdated.modifiedBy should be (Some("UnitTestsUser"))
      limitUpdated.modifiedDateTime should not be empty
      limitUpdated.expired should be (false)
      limitUpdated.typeId should be (limitToUpdate.typeId)
      limitUpdated.timeStamp should be (limitToUpdate.timeStamp)

      //Verify if old asset is expired
      val limitExpired = linearAssetDao.fetchLinearAssetsByIds(Set(11111), "mittarajoitus").head
      limitExpired.expired should be (true)
    }
  }

  test("Update numerical limit and verify asset") {
    runWithRollback {
      //Update Numeric Values By Expiring the Old Asset (asset type 30)
      val limitToUpdate = linearAssetDao.fetchLinearAssetsByIds(Set(11111), "mittarajoitus").head
      val newAssetIdCreatedWithUpdate = ServiceWithDao.update(Seq(11111l), NumericValue(2000), "TestsUser")

      //Verify if the new data of the new asset is equal to old asset
      val limitUpdated = linearAssetDao.fetchLinearAssetsByIds(newAssetIdCreatedWithUpdate.toSet, "mittarajoitus").head

      limitUpdated.id should not be limitToUpdate.id
      limitUpdated.expired should be (false)
      limitUpdated.verifiedBy should be (Some("TestsUser"))
      limitUpdated.verifiedDate.get.toString("yyyy-MM-dd") should be (DateTime.now().toString("yyyy-MM-dd"))

      //Verify if old asset is expired
      val limitExpired = linearAssetDao.fetchLinearAssetsByIds(Set(11111), "mittarajoitus").head
      limitExpired.expired should be (true)
    }
  }

}
