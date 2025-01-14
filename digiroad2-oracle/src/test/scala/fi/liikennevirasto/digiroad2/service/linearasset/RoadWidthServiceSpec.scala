package fi.liikennevirasto.digiroad2.service.linearasset

import fi.liikennevirasto.digiroad2.asset.SideCode.BothDirections
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.vvh.ChangeInfo
import fi.liikennevirasto.digiroad2.client.{FeatureClass, RoadLinkFetched}
import fi.liikennevirasto.digiroad2.dao.{DynamicLinearAssetDao, MunicipalityDao}
import fi.liikennevirasto.digiroad2.dao.linearasset.{AssetLastModification, PostGISLinearAssetDao}
import fi.liikennevirasto.digiroad2.linearasset.LinearAssetFiller._
import fi.liikennevirasto.digiroad2.linearasset._
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.util.{LinearAssetUtils, LinkIdGenerator, PolygonTools, TestTransactions}
import fi.liikennevirasto.digiroad2.{DigiroadEventBus, DummyEventBus, GeometryUtils, Point}
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FunSuite, Matchers}
import slick.driver.JdbcDriver.backend.Database.dynamicSession


class RoadWidthServiceSpec extends FunSuite with Matchers {
  val RoadWidthAssetTypeId = 120

  val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
  val mockPolygonTools = MockitoSugar.mock[PolygonTools]
  val mockLinearAssetDao = MockitoSugar.mock[PostGISLinearAssetDao]
  val mockDynamicLinearAssetDao = MockitoSugar.mock[DynamicLinearAssetDao]
  val mockEventBus = MockitoSugar.mock[DigiroadEventBus]
  val linearAssetDao = new PostGISLinearAssetDao()
  val mockMunicipalityDao = MockitoSugar.mock[MunicipalityDao]

  val linkId = LinkIdGenerator.generateRandom()
  val roadLinkWithLinkSource = RoadLink(
    linkId, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, Municipality,
    1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235), "SURFACETYPE" -> BigInt(2)), ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)
  when(mockRoadLinkService.getRoadLinksAndComplementariesByLinkIds(any[Set[String]], any[Boolean])).thenReturn(Seq(roadLinkWithLinkSource))

  val initChangeSet: ChangeSet = ChangeSet(droppedAssetIds = Set.empty[Long],
                                           expiredAssetIds = Set.empty[Long],
                                           adjustedMValues = Seq.empty[MValueAdjustment],
                                           adjustedVVHChanges = Seq.empty[VVHChangesAdjustment],
                                           adjustedSideCodes = Seq.empty[SideCodeAdjustment],
                                           valueAdjustments = Seq.empty[ValueAdjustment])

  val randomLinkId1: String = LinkIdGenerator.generateRandom()
  val randomLinkId2: String = LinkIdGenerator.generateRandom()
  val randomLinkId3: String = LinkIdGenerator.generateRandom()
  val randomLinkId4: String = LinkIdGenerator.generateRandom()
  val randomLinkId5: String = LinkIdGenerator.generateRandom()

  val dynamicLinearAssetDAO = new DynamicLinearAssetDao

  object ServiceWithDao extends RoadWidthService(mockRoadLinkService, mockEventBus) {
    override def withDynTransaction[T](f: => T): T = f
    override def withDynSession[T](f: => T): T = f
    override def roadLinkService: RoadLinkService = mockRoadLinkService
    override def dao: PostGISLinearAssetDao = linearAssetDao
    override def eventBus: DigiroadEventBus = mockEventBus
    override def polygonTools: PolygonTools = mockPolygonTools
    override def municipalityDao: MunicipalityDao = mockMunicipalityDao

    override def getUncheckedLinearAssets(areas: Option[Set[Int]]) = throw new UnsupportedOperationException("Not supported method")
  }

  def runWithRollback(test: => Unit): Unit = assetLock.synchronized {
    TestTransactions.runWithRollback()(test)
  }

  val assetLock = "Used to prevent deadlocks"

  private def createChangeInfo(roadLinks: Seq[RoadLink], timeStamp: Long) = {
    roadLinks.map(rl => ChangeInfo(Some(rl.linkId), Some(rl.linkId), 0L, 1, None, None, None, None, timeStamp))
  }

  private def createService() = {
    val service = new RoadWidthService(mockRoadLinkService, new DummyEventBus) {
      override def withDynTransaction[T](f: => T): T = f
      override def withDynSession[T](f: => T): T = f
    }
    service
  }

  private def createRoadLinks(municipalityCode: Int) = {
    val newLinkId1 = randomLinkId1
    val newLinkId2 = randomLinkId2
    val newLinkId3 = randomLinkId3
    val newLinkId4 = randomLinkId4
    val administrativeClass = Municipality
    val trafficDirection = TrafficDirection.BothDirections
    val functionalClass = 1
    val linkType = Freeway
    val attributes1 = Map("MUNICIPALITYCODE" -> BigInt(municipalityCode), "SURFACETYPE" -> BigInt(2), "MTKCLASS" -> BigInt(12112))
    val attributes2 = Map("MUNICIPALITYCODE" -> BigInt(municipalityCode), "SURFACETYPE" -> BigInt(2), "MTKCLASS" -> BigInt(12122))
    val attributes3 = Map("MUNICIPALITYCODE" -> BigInt(municipalityCode), "SURFACETYPE" -> BigInt(2), "MTKCLASS" -> BigInt(2))

    val geometry = List(Point(0.0, 0.0), Point(20.0, 0.0))
    val newRoadLink1 = RoadLink(newLinkId1, geometry, GeometryUtils.geometryLength(geometry), administrativeClass,
      functionalClass, trafficDirection, linkType, None, None, attributes1)
    val newRoadLink2 = newRoadLink1.copy(linkId=newLinkId2, attributes = attributes2)
    val newRoadLink3 = newRoadLink1.copy(linkId=newLinkId3, attributes = attributes3)
    val newRoadLink4 = newRoadLink1.copy(linkId=newLinkId4, attributes = attributes3)
    List(newRoadLink1, newRoadLink2, newRoadLink3, newRoadLink4)
  }

  ignore("Should be created only 1 new road width asset when get 3 roadlink change information from vvh and only 1 roadlink have MTKClass valid") {

    val service = new RoadWidthService(mockRoadLinkService, new DummyEventBus) {
      override def withDynTransaction[T](f: => T): T = f
    }

    val newLinkId2 = randomLinkId3
    val newLinkId1 = randomLinkId2
    val newLinkId0 = randomLinkId1
    val municipalityCode = 235
    val administrativeClass = Municipality
    val trafficDirection = TrafficDirection.BothDirections
    val functionalClass = 1
    val linkType = Freeway
    val boundingBox = BoundingRectangle(Point(123, 345), Point(567, 678))
    val attributes0 = Map("MUNICIPALITYCODE" -> BigInt(municipalityCode), "SURFACETYPE" -> BigInt(2), "MTKCLASS" -> BigInt(12112))
    val attributes1 = Map("MUNICIPALITYCODE" -> BigInt(municipalityCode), "SURFACETYPE" -> BigInt(2), "MTKCLASS" -> BigInt(100))
    val attributes2 = Map("MUNICIPALITYCODE" -> BigInt(municipalityCode), "SURFACETYPE" -> BigInt(2), "MTKCLASS" -> BigInt(2))
    val timeStamp = 14440000

    val newRoadLink2 = RoadLink(newLinkId2, List(Point(0.0, 0.0), Point(20.0, 0.0)), 20.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, attributes2)
    val newRoadLink1 = RoadLink(newLinkId1, List(Point(0.0, 0.0), Point(20.0, 0.0)), 20.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, attributes1)
    val newRoadLink0 = RoadLink(newLinkId0, List(Point(0.0, 0.0), Point(20.0, 0.0)), 20.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, attributes0)

    val changeInfoSeq = Seq(ChangeInfo(Some(newLinkId2), Some(newLinkId2), 12345, 1, Some(0), Some(10), Some(0), Some(10), timeStamp),
      ChangeInfo(Some(newLinkId1), Some(newLinkId1), 12345, 1, Some(0), Some(10), Some(0), Some(10), timeStamp),
      ChangeInfo(Some(newLinkId0), Some(newLinkId0), 12345, 1, Some(0), Some(10), Some(0), Some(10), timeStamp)
    )

    runWithRollback {
      when(mockRoadLinkService.getRoadLinksAndChanges(any[BoundingRectangle], any[Set[Int]],any[Boolean])).thenReturn((List(newRoadLink2, newRoadLink1, newRoadLink0), changeInfoSeq))
      when(mockLinearAssetDao.fetchLinearAssetsByLinkIds(any[Int], any[Seq[String]], any[String], any[Boolean])).thenReturn(List())

      val existingAssets = service.getByBoundingBox(RoadWidthAssetTypeId, boundingBox).toList.flatten

      val filteredCreatedAssets = existingAssets.filter(p => p.linkId == newLinkId0 && p.value.isDefined)

      existingAssets.length should be (3)
      filteredCreatedAssets.length should be (1)
      filteredCreatedAssets.head.typeId should be (RoadWidthAssetTypeId)
      filteredCreatedAssets.head.value should be (Some(NumericValue(1100)))
      filteredCreatedAssets.head.timeStamp should be (timeStamp)
    }
  }

  test("Should not created road width asset when exists an asset created by UI (same linkid)") {

    val municipalityCode = 235
    val roadLinks = createRoadLinks(municipalityCode)
    val service = createService()

    val assets = Seq(PersistedLinearAsset(1, randomLinkId1, 1, Some(NumericValue(12000)), 0, 5, None, None, None, None, false, RoadWidthAssetTypeId, 0, None, LinkGeomSource.NormalLinkInterface, None, None, None))
    runWithRollback {
      val changeInfo = createChangeInfo(roadLinks, 11L)
      val (newAssets, changeSet) = service.getRoadWidthAssetChanges(assets, Seq(), roadLinks , changeInfo, _ => Seq(), initChangeSet )
      changeSet.expiredAssetIds should have size 0
      newAssets.filter(_.linkId == randomLinkId1) should have size 0
      newAssets.filter(_.linkId == randomLinkId2) should have size 1
      newAssets.filter(_.linkId == randomLinkId2).head.value should be(Some(NumericValue(650)))
    }
  }

  test("Should not create any new asset (MTKClass not valid)") {

    val municipalityCode = 235
    val newLinkId1 = randomLinkId1
    val newLinkId2 = randomLinkId2
    val administrativeClass = Municipality
    val trafficDirection = TrafficDirection.BothDirections
    val functionalClass = 1
    val linkType = Freeway
    val attributes1 = Map("MUNICIPALITYCODE" -> BigInt(municipalityCode), "SURFACETYPE" -> BigInt(2), "MTKCLASS" -> BigInt(120))
    val attributes2 = Map("MUNICIPALITYCODE" -> BigInt(municipalityCode), "SURFACETYPE" -> BigInt(2), "MTKCLASS" -> BigInt(2))

    runWithRollback {
      val geometry = List(Point(0.0, 0.0), Point(20.0, 0.0))
      val newRoadLink1 = RoadLink(newLinkId1, geometry, GeometryUtils.geometryLength(geometry), administrativeClass,
        functionalClass, trafficDirection, linkType, None, None, attributes1)
      val newRoadLink2 = newRoadLink1.copy(linkId = newLinkId2, attributes = attributes2)
      val roadLinks = List(newRoadLink1, newRoadLink2)
      val service = createService()

      val changeInfo = createChangeInfo(roadLinks, 11L)
      val (newAsset, changeSet) = service.getRoadWidthAssetChanges(Seq(), Seq(), roadLinks, changeInfo, _ => Seq(), initChangeSet)
      changeSet.expiredAssetIds should have size 0
      newAsset should have size 0
    }
  }

  test("Should not create new road width if the road doesn't have MTKClass attribute") {

    val newLinkId2 = randomLinkId2
    val newLinkId1 = randomLinkId1
    val municipalityCode = 235
    val administrativeClass = Municipality
    val trafficDirection = TrafficDirection.BothDirections
    val functionalClass = 1
    val linkType = Freeway

    val attributes1 = Map("MUNICIPALITYCODE" -> BigInt(municipalityCode), "SURFACETYPE" -> BigInt(2))
    val attributes2 = Map("MUNICIPALITYCODE" -> BigInt(municipalityCode), "SURFACETYPE" -> BigInt(2))

    runWithRollback {
      val geometry = List(Point(0.0, 0.0), Point(20.0, 0.0))
      val newRoadLink1 = RoadLink(newLinkId1, geometry, GeometryUtils.geometryLength(geometry), administrativeClass,
        functionalClass, trafficDirection, linkType, None, None, attributes1)
      val newRoadLink2 = newRoadLink1.copy(linkId = newLinkId2, attributes = attributes2)
      val roadLinks = List(newRoadLink1, newRoadLink2)
      val service = createService()

      val changeInfo = createChangeInfo(roadLinks, 11L)
      val (newAsset, changeSet) = service.getRoadWidthAssetChanges(Seq(), Seq(), roadLinks, changeInfo, _ => Seq(), initChangeSet)
      changeSet.expiredAssetIds should have size 0
      newAsset should have size 0
    }
  }

  test("Only update road width assets auto generated ") {
    val municipalityCode = 235
    val roadLinks = createRoadLinks(municipalityCode)
    val service = createService()

    val assets = Seq(PersistedLinearAsset(1, randomLinkId1, 1, Some(NumericValue(4000)), 0, 20,  Some(AutoGeneratedUsername.mtkClassDefault), None, None, None, false, RoadWidthAssetTypeId, 10L, None, LinkGeomSource.NormalLinkInterface, None, None, None),
      PersistedLinearAsset(2, randomLinkId2, 1, Some(NumericValue(2000)), 0, 20, None, None, None, None, false, RoadWidthAssetTypeId, 10L, None, LinkGeomSource.NormalLinkInterface, None, None, None))
    runWithRollback {
      val changeInfo = createChangeInfo(roadLinks, 11L)
      val (newAsset, changeSet) = service.getRoadWidthAssetChanges(assets, Seq(), roadLinks, changeInfo, _ => Seq(), initChangeSet)
      changeSet.expiredAssetIds should have size 1
      changeSet.expiredAssetIds should be(Set(1))
      newAsset.forall(_.timeStamp == 11L) should be(true)
      newAsset.forall(_.value.isDefined) should be(true)
      newAsset should have size 1
      newAsset.head.linkId should be(randomLinkId1)
      newAsset.head.value should be(Some(NumericValue(1100)))
    }
  }

  test("Do not updated asset created or expired by the user") {
    val municipalityCode = 235
    val roadLinks = createRoadLinks(municipalityCode)
    val service = createService()

    val assets = Seq(PersistedLinearAsset(1, randomLinkId1, 1, Some(NumericValue(4000)), 0, 20,  Some("test"), None, None, None, false, RoadWidthAssetTypeId, 10L, None, LinkGeomSource.NormalLinkInterface, None, None, None))
    val expiredAssets = Seq(AssetLastModification(2, randomLinkId2, Some("test2"), None))

    runWithRollback {
      val changeInfo = createChangeInfo(roadLinks, 11L)
      val (newAsset, changeSet) = service.getRoadWidthAssetChanges(assets, Seq(), roadLinks, changeInfo, _ => expiredAssets, initChangeSet)
      changeSet.expiredAssetIds should have size 0
      newAsset should have size 0
    }
  }

  ignore("Create linear asset on a road link that has changed previously"){
    val oldLinkId1 = randomLinkId1
    val linkId1 = randomLinkId2
    val newLinkId = randomLinkId5
    val municipalityCode = 235
    val administrativeClass = Municipality
    val trafficDirection = TrafficDirection.BothDirections
    val functionalClass = 1
    val linkType = Freeway
    val service = createService()

    val roadLinks = Seq(
      RoadLink(linkId1, List(Point(0.0, 0.0), Point(20.0, 0.0)), 20.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, Map("MUNICIPALITYCODE" -> BigInt(municipalityCode), "SURFACETYPE" -> BigInt(2))),
      RoadLink(newLinkId, List(Point(0.0, 0.0), Point(120.0, 0.0)), 120.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, Map("MUNICIPALITYCODE" -> BigInt(municipalityCode), "SURFACETYPE" -> BigInt(2))))

    val changeInfo = Seq(
      ChangeInfo(Some(oldLinkId1), Some(newLinkId), 12345, 1, Some(0), Some(100), Some(0), Some(100), 1476468913000L),
      ChangeInfo(Some(linkId1), Some(newLinkId), 12345, 2, Some(0), Some(20), Some(100), Some(120), 1476468913000L)
    )

    PostGISDatabase.withDynTransaction {
      when(mockRoadLinkService.getRoadLinksAndChanges(any[BoundingRectangle], any[Set[Int]],any[Boolean])).thenReturn((roadLinks, changeInfo))
      when(mockRoadLinkService.getRoadLinksAndComplementariesByLinkIds(any[Set[String]], any[Boolean])).thenReturn(roadLinks)
      val newAsset1 = NewLinearAsset(linkId1, 0.0, 20, NumericValue(2017), 1, 234567, None)
      val id1 = service.create(Seq(newAsset1), RoadWidthAssetTypeId, "KX2")

      val newAsset = NewLinearAsset(newLinkId, 0.0, 120, NumericValue(4779), 1, 234567, None)
      val id = service.create(Seq(newAsset), RoadWidthAssetTypeId, "KX2")

      id should have size 1
      id.head should not be 0

      val assets = service.getPersistedAssetsByIds(RoadWidthAssetTypeId, Set(1L, id.head, id1.head))
      assets should have size 2
      assets.forall(_.timeStamp > 0L) should be (true)

      val after = service.getByBoundingBox(RoadWidthAssetTypeId, BoundingRectangle(Point(0.0, 0.0), Point(120.0, 120.0)), Set(municipalityCode))
      after should have size 2
      after.flatten.forall(_.id != 0) should be (true)
      dynamicSession.rollback()
    }
  }

  test("get unVerified road width assets") {
    val linkId1 = randomLinkId2
    val linkId2 = randomLinkId3
    val municipalityCode = 235
    val administrativeClass = Municipality
    val trafficDirection = TrafficDirection.BothDirections
    val functionalClass = 1
    val linkType = Freeway
    val service = createService()

    val roadLinks = Seq(
      RoadLink(linkId1, List(Point(0.0, 0.0), Point(20.0, 0.0)), 20.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, Map("MUNICIPALITYCODE" -> BigInt(municipalityCode), "SURFACETYPE" -> BigInt(2))),
      RoadLink(linkId2, List(Point(0.0, 0.0), Point(120.0, 0.0)), 120.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, Map("MUNICIPALITYCODE" -> BigInt(municipalityCode), "SURFACETYPE" -> BigInt(2))))

    PostGISDatabase.withDynTransaction {
      when(mockMunicipalityDao.getMunicipalityNameByCode(235)).thenReturn("Kauniainen")
      when(mockRoadLinkService.getRoadLinksAndChanges(any[BoundingRectangle], any[Set[Int]],any[Boolean])).thenReturn((roadLinks, Nil))
      when(mockRoadLinkService.getRoadLinksAndComplementariesByLinkIds(any[Set[String]], any[Boolean])).thenReturn(roadLinks)

      val newAssets1 = service.create(Seq(NewLinearAsset(linkId1, 0.0, 20, NumericValue(2017), 1, 234567, None)), RoadWidthAssetTypeId, AutoGeneratedUsername.dr1Conversion)
      val newAssets2 = service.create(Seq(NewLinearAsset(linkId2, 40.0, 120, NumericValue(4779), 1, 234567, None)), RoadWidthAssetTypeId, "testuser")

      val unVerifiedAssets = service.getUnverifiedLinearAssets(RoadWidthAssetTypeId, Set())
      unVerifiedAssets.keys.head should be ("Kauniainen")
      unVerifiedAssets.flatMap(_._2).keys.head should be("Municipality")
      unVerifiedAssets.flatMap(_._2).values.head should be(newAssets1)
      unVerifiedAssets.flatMap(_._2).values.head should not be newAssets2
      dynamicSession.rollback()
    }
  }

  test("create roadWidth and check if informationSource is Municipality Maintainer "){

    val service = createService()
    val toInsert = Seq(NewLinearAsset(randomLinkId1, 0, 50, NumericValue(4000), BothDirections.value, 0, None), NewLinearAsset(randomLinkId2, 0, 50, NumericValue(3000), BothDirections.value, 0, None))
    runWithRollback {
      val assetsIds = service.create(toInsert, RoadWidth.typeId, "test")
      val assetsCreated = service.getPersistedAssetsByIds(RoadWidth.typeId, assetsIds.toSet)

      assetsCreated.length should be (2)
      assetsCreated.foreach{asset =>
        asset.informationSource should be (Some(MunicipalityMaintenainer))
      }
    }
  }

  test("check if roadWidth created because of changes has informationSource as MmlNls") {
    val municipalityCode = 235
    val roadLinks = createRoadLinks(municipalityCode)
    val service = createService()

    val assets = Seq(PersistedLinearAsset(1, randomLinkId1, 1, Some(NumericValue(12000)), 0, 5, None, None, None, None, false, RoadWidthAssetTypeId, 0, None, LinkGeomSource.NormalLinkInterface, None, None, None))
    runWithRollback {
      val changeInfo = createChangeInfo(roadLinks, 11L)
      val (newAssets, changeSet) = service.getRoadWidthAssetChanges(assets, Seq(), roadLinks, changeInfo, _ => Seq(), initChangeSet)
      changeSet.expiredAssetIds should have size 0
      newAssets.foreach { asset =>
        asset.informationSource should be(Some(MmlNls))

      }
    }
  }

  test("update roadWidth and check if informationSource is Municipality Maintainer "){
    val propSuggestBox = DynamicProperty("suggest_box", "checkbox", false, List(DynamicPropertyValue(0)))

    val propInsWidth1 = DynamicProperty("width", "integer", true, Seq(DynamicPropertyValue("4000")))
    val propIns1: Seq[DynamicProperty] = List(propInsWidth1, propSuggestBox)
    val propInsWidth2 = DynamicProperty("width", "integer", true, Seq(DynamicPropertyValue("3000")))
    val propIns2: Seq[DynamicProperty] = List(propInsWidth2, propSuggestBox)

    val propUpdWidth = DynamicProperty("width", "integer", true, Seq(DynamicPropertyValue("1500")))
    val propUpd: Seq[DynamicProperty] = List(propSuggestBox, propUpdWidth)

    val roadWidthIns1 = DynamicValue(DynamicAssetValue(propIns1))
    val roadWidthIns2 = DynamicValue(DynamicAssetValue(propIns2))
    val roadWidthUpd = DynamicValue(DynamicAssetValue(propUpd))

    when(mockRoadLinkService.fetchNormalOrComplimentaryRoadLinkByLinkId(any[String])).thenReturn(Some(RoadLinkFetched(randomLinkId1, 235, Seq(Point(0, 0), Point(100, 0)), Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)))

    val service = createService()
    val toInsert = Seq(NewLinearAsset(randomLinkId1, 0, 50, roadWidthIns1, BothDirections.value, 0, None), NewLinearAsset(randomLinkId2, 0, 50, roadWidthIns2, BothDirections.value, 0, None))
    runWithRollback {
      val assetsIds = service.create(toInsert, RoadWidth.typeId, "test")
      val updated = service.update(assetsIds, roadWidthUpd, "userTest")

      val assetsUpdated = service.getPersistedAssetsByIds(RoadWidth.typeId, updated.toSet)

      assetsUpdated.length should be (2)
      assetsUpdated.foreach{asset =>
        asset.informationSource should be (Some(MunicipalityMaintenainer))
        asset.value.head.asInstanceOf[DynamicValue].value.properties.find(_.publicId == "width") should be (roadWidthUpd.value.properties.find(_.publicId == "width"))
      }
    }
  }

}

