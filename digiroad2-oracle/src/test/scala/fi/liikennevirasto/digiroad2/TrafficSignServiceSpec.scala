package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.asset.LinkGeomSource.NormalLinkInterface
import fi.liikennevirasto.digiroad2.asset.SideCode.{AgainstDigitizing, BothDirections, TowardsDigitizing}
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.linearasset.RoadLink
import fi.liikennevirasto.digiroad2.user.oracle.OracleUserProvider
import fi.liikennevirasto.digiroad2.user.{Configuration, User}
import fi.liikennevirasto.digiroad2.util.TestTransactions
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import org.scalatest.{BeforeAndAfter, FunSuite, Matchers}

class TrafficSignServiceSpec extends FunSuite with Matchers with BeforeAndAfter {
  def toRoadLink(l: VVHRoadlink) = {
    RoadLink(l.linkId, l.geometry, GeometryUtils.geometryLength(l.geometry),
      l.administrativeClass, 1, l.trafficDirection, UnknownLinkType, None, None, l.attributes + ("MUNICIPALITYCODE" -> BigInt(l.municipalityCode)))
  }

  val testUser = User(
    id = 1,
    username = "Hannu",
    configuration = Configuration(authorizedMunicipalities = Set(235)))
  val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
  val mockUserProvider = MockitoSugar.mock[OracleUserProvider]
  when(mockRoadLinkService.getRoadLinksFromVVH(any[BoundingRectangle], any[Set[Int]])).thenReturn(Seq(
    VVHRoadlink(1611317, 235, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), Municipality,
      TrafficDirection.BothDirections, FeatureClass.AllOthers)).map(toRoadLink))
  when(mockRoadLinkService.getRoadLinkFromVVH(any[Long], any[Boolean])).thenReturn(Seq(
    VVHRoadlink(1611317, 235, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), Municipality,
      TrafficDirection.BothDirections, FeatureClass.AllOthers)).map(toRoadLink).headOption)
  when(mockRoadLinkService.getClosestRoadlinkForCarTrafficFromVVH(any[User], any[Point])).thenReturn(Seq(
    VVHRoadlink(1611400, 235, Seq(Point(2, 2), Point(4, 4)), Municipality,
      TrafficDirection.BothDirections, FeatureClass.AllOthers)).headOption)
  when(mockRoadLinkService.getRoadLinkFromVVH(1611400)).thenReturn(Seq(
    VVHRoadlink(1611400, 235, Seq(Point(2, 2), Point(4, 4)), Municipality,
      TrafficDirection.BothDirections, FeatureClass.AllOthers)).map(toRoadLink).headOption)
  when(mockUserProvider.getCurrentUser()).thenReturn(testUser)

  when(mockRoadLinkService.getRoadLinkFromVVH(1191950690)).thenReturn(Seq(
    VVHRoadlink(1191950690, 235, Seq(Point(373500.349, 6677657.152), Point(373494.182, 6677669.918)), Private,
      TrafficDirection.BothDirections, FeatureClass.AllOthers)).map(toRoadLink).headOption)
  val userProvider = new OracleUserProvider
  val service = new TrafficSignService(mockRoadLinkService, mockUserProvider) {
    override def withDynTransaction[T](f: => T): T = f

    override def withDynSession[T](f: => T): T = f
  }

  def runWithRollback(test: => Unit): Unit = TestTransactions.runWithRollback(service.dataSource)(test)

  test("Can fetch by bounding box") {
    when(mockRoadLinkService.getRoadLinksWithComplementaryAndChangesFromVVH(any[BoundingRectangle], any[Set[Int]], any[Boolean])).thenReturn((List(), Nil))

    runWithRollback {
      val result = service.getByBoundingBox(testUser, BoundingRectangle(Point(374466.5, 6677346.5), Point(374467.5, 6677347.5))).head
      result.id should equal(600073)
      result.linkId should equal(1611317)
      result.lon should equal(374467)
      result.lat should equal(6677347)
      result.mValue should equal(103)
    }
  }

  test("Can fetch by municipality") {
    when(mockRoadLinkService.getRoadLinksWithComplementaryAndChangesFromVVH(235)).thenReturn((Seq(
      VVHRoadlink(388553074, 235, Seq(Point(0.0, 0.0), Point(200.0, 0.0)), Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers)).map(toRoadLink), Nil))

    runWithRollback {
      val result = service.getByMunicipality(235).find(_.id == 600073).get

      result.id should equal(600073)
      result.linkId should equal(1611317)
      result.lon should equal(374467)
      result.lat should equal(6677347)
      result.mValue should equal(103)
    }
  }

  test("Expire Traffic Sign") {
    runWithRollback {
      service.getPersistedAssetsByIds(Set(600073)).length should be(1)
      service.expire(600073, testUser.username)
      service.getPersistedAssetsByIds(Set(600073)) should be(Nil)
    }
  }

  test("Create new Traffic Sign") {
    runWithRollback {
      val properties = Set(
        SimpleProperty("trafficSigns_type", List(PropertyValue("1"))),
        SimpleProperty("trafficSigns_value", List(PropertyValue("80"))),
        SimpleProperty("trafficSigns_info", List(PropertyValue("Additional Info for test"))))

      val roadLink = RoadLink(388553075, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10, Municipality, 1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val id = service.create(IncomingTrafficSign(2.0, 0.0, 388553075, properties, 1, None), testUser.username, roadLink)

      val assets = service.getPersistedAssetsByIds(Set(id))

      assets.size should be(1)

      val asset = assets.head

      asset.id should be(id)
      asset.linkId should be(388553075)
      asset.lon should be(2)
      asset.lat should be(0)
      asset.mValue should be(2)
      asset.floating should be(false)
      asset.municipalityCode should be(235)
      asset.propertyData.find(p => p.publicId == "trafficSigns_type").get.values.head.propertyValue should be ("1")
      asset.propertyData.find(p => p.publicId == "trafficSigns_value").get.values.head.propertyValue should be ("80")
      asset.propertyData.find(p => p.publicId == "trafficSigns_info").get.values.head.propertyValue should be ("Additional Info for test")
      asset.createdBy should be(Some(testUser.username))
      asset.createdAt shouldBe defined
    }
  }

  test("Update Traffic Sign") {
    runWithRollback {
      val trafficSign = service.getById(600073).get

      val updatedProperties = Set(
        SimpleProperty("trafficSigns_type", List(PropertyValue("2"))),
        SimpleProperty("trafficSigns_value", List(PropertyValue("90"))),
        SimpleProperty("trafficSigns_info", List(PropertyValue("Updated Additional Info for test"))))
      val updated = IncomingTrafficSign(trafficSign.lon, trafficSign.lat, trafficSign.linkId, updatedProperties, 1, None)

      service.update(trafficSign.id, updated, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 235, "unit_test", linkSource = NormalLinkInterface)
      val updatedTrafficSign = service.getById(600073).get

      updatedTrafficSign.propertyData.find(p => p.publicId == "trafficSigns_type").get.values.head.propertyValue should be ("2")
      updatedTrafficSign.propertyData.find(p => p.publicId == "trafficSigns_value").get.values.head.propertyValue should be ("90")
      updatedTrafficSign.propertyData.find(p => p.publicId == "trafficSigns_info").get.values.head.propertyValue should be ("Updated Additional Info for test")
      updatedTrafficSign.id should equal(updatedTrafficSign.id)
      updatedTrafficSign.modifiedBy should equal(Some("unit_test"))
      updatedTrafficSign.modifiedAt shouldBe defined
    }
  }

  test("Create traffic sign with direction towards digitizing using coordinates without asset bearing") {
    /*mock road link is set to (2,2), (4,4), so this asset is set to go towards digitizing*/
    runWithRollback {
      val id = service.createFromCoordinates(3, 2, TRTrafficSignType.SpeedLimit, Some(100), Some(false), TrafficDirection.UnknownDirection, None)
      val assets = service.getPersistedAssetsByIds(Set(id.get.toString.toLong))
      assets.size should be(1)
      val asset = assets.head
      asset.id should be(id.get)
      asset.propertyData.find(p => p.publicId == "trafficSigns_type").get.values.head.propertyValue should be ("1")
      asset.propertyData.find(p => p.publicId == "trafficSigns_value").get.values.head.propertyValue should be ("100")
      asset.validityDirection should be (TowardsDigitizing.value)
      asset.bearing.get should be (45)
    }
  }

  test("Create traffic sign with direction against digitizing using coordinates without asset bearing") {
     /*mock road link is set to (2,2), (4,4), so this asset is set to go against digitizing*/
    runWithRollback {
      val id = service.createFromCoordinates(3, 4, TRTrafficSignType.SpeedLimit, Some(100), Some(false), TrafficDirection.UnknownDirection, None)
      val assets = service.getPersistedAssetsByIds(Set(id.get.toString.toLong))
      assets.size should be(1)
      val asset = assets.head
      asset.id should be(id.get)
      asset.propertyData.find(p => p.publicId == "trafficSigns_type").get.values.head.propertyValue should be ("1")
      asset.propertyData.find(p => p.publicId == "trafficSigns_value").get.values.head.propertyValue should be ("100")
      asset.validityDirection should be (AgainstDigitizing.value)
    }
  }

  test("Create traffic sign with direction towards digitizing using coordinates with asset bearing") {
    /*asset bearing in this case indicates towards which direction the traffic sign is facing, not the flow of traffic*/
    runWithRollback {
      val id = service.createFromCoordinates(3, 2, TRTrafficSignType.SpeedLimit, Some(100), Some(false), TrafficDirection.UnknownDirection, Some(225))
      val assets = service.getPersistedAssetsByIds(Set(id.get.toString.toLong))
      assets.size should be(1)
      val asset = assets.head
      asset.id should be(id.get)
      asset.propertyData.find(p => p.publicId == "trafficSigns_type").get.values.head.propertyValue should be ("1")
      asset.propertyData.find(p => p.publicId == "trafficSigns_value").get.values.head.propertyValue should be ("100")
      asset.validityDirection should be (TowardsDigitizing.value)
      asset.bearing.get should be (45)
    }
  }

  test("Create traffic sign with direction against digitizing using coordinates with asset bearing") {
    /*asset bearing in this case indicates towards which direction the traffic sign is facing, not the flow of traffic*/
    runWithRollback {
      val id = service.createFromCoordinates(3, 4, TRTrafficSignType.SpeedLimit, Some(100), Some(false), TrafficDirection.UnknownDirection, Some(45))
      val assets = service.getPersistedAssetsByIds(Set(id.get.toString.toLong))
      assets.size should be(1)
      val asset = assets.head
      asset.id should be(id.get)
      asset.propertyData.find(p => p.publicId == "trafficSigns_type").get.values.head.propertyValue should be ("1")
      asset.propertyData.find(p => p.publicId == "trafficSigns_value").get.values.head.propertyValue should be ("100")
      asset.validityDirection should be(AgainstDigitizing.value)
    }
  }
  test("two-sided traffic signs are effective in both directions ") {
    runWithRollback {
      val id = service.createFromCoordinates(3, 4, TRTrafficSignType.PedestrianCrossing, None, Some(true), TrafficDirection.UnknownDirection, Some(45))
      val assets = service.getPersistedAssetsByIds(Set(id.get.toString.toLong))
      assets.size should be(1)
      val asset = assets.head
      asset.id should be(id.get)
      asset.propertyData.find(p => p.publicId == "trafficSigns_type").get.values.head.propertyValue should be ("7")
      asset.validityDirection should be(BothDirections.value)

    }
  }
}
