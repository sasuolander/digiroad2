package fi.liikennevirasto.digiroad2.service.linearasset

import fi.liikennevirasto.digiroad2.DigiroadEventBus
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.vvh.{VVHClient, VVHRoadLinkClient}
import fi.liikennevirasto.digiroad2.dao.linearasset.OracleLinearAssetDao
import fi.liikennevirasto.digiroad2.linearasset.MultiValue
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.util.PolygonTools
import org.scalatest.{FunSuite, Matchers}
import org.scalatest.mock.MockitoSugar

class MultiValueAssetServiceSpec extends FunSuite with Matchers {
  val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
  val mockVVHClient = MockitoSugar.mock[VVHClient]
  val mockVVHRoadLinkClient = MockitoSugar.mock[VVHRoadLinkClient]
  val mockPolygonTools = MockitoSugar.mock[PolygonTools]

  val mockLinearAssetDao = MockitoSugar.mock[OracleLinearAssetDao]
  val mockEventBus = MockitoSugar.mock[DigiroadEventBus]
  val linearAssetDao = new OracleLinearAssetDao(mockVVHClient, mockRoadLinkService)

  object ServiceWithDao extends TextValueLinearAssetService(mockRoadLinkService, mockEventBus) {
    override def withDynTransaction[T](f: => T): T = f
    override def roadLinkService: RoadLinkService = mockRoadLinkService
    override def dao: OracleLinearAssetDao = linearAssetDao
    override def eventBus: DigiroadEventBus = mockEventBus
    override def vvhClient: VVHClient = mockVVHClient
    override def polygonTools: PolygonTools = mockPolygonTools
  }

  test("equals returns false when objects are not matching"){
    val asset1 = MultiValue(Seq(MultiTypeProperty("name", PropertyTypes.Text, Seq(MultiTypePropertyValue("Asset1Name")))))
    val asset2 = MultiValue(Seq(MultiTypeProperty("name", PropertyTypes.Text, Seq(MultiTypePropertyValue("Asset2Name")))))
    val asset3 = MultiValue(Seq(MultiTypeProperty("name", PropertyTypes.Text, Seq(MultiTypePropertyValue("BigName"), MultiTypePropertyValue("ExtraName")))))
    val asset4 = MultiValue(Seq(MultiTypeProperty("name", PropertyTypes.Text, Seq(MultiTypePropertyValue("BigName")))))
    val asset5 = MultiValue(Seq(MultiTypeProperty("name", PropertyTypes.ReadOnlyNumber, Seq(MultiTypePropertyValue("BiggerName")))))
    val asset6 = MultiValue(Seq(MultiTypeProperty("name", PropertyTypes.Text, Seq(MultiTypePropertyValue("BiggerName")))))
    asset1.equals(asset2) should be (false)
    asset3.equals(asset4) should be (false)
    asset5.equals(asset6) should be (false)
  }

  test("equals returns true when objects are matching"){
    val ratings = MultiTypeProperty("ratings", PropertyTypes.ReadOnlyNumber, Seq(MultiTypePropertyValue(1), MultiTypePropertyValue(10)))
    val choice1 = MultiTypeProperty("choices", PropertyTypes.Text, Seq(MultiTypePropertyValue("Strawberry"), MultiTypePropertyValue("Chocolate"), MultiTypePropertyValue("Vanilla")))
    val choice2 = MultiTypeProperty("choices", PropertyTypes.Text, Seq(MultiTypePropertyValue("Vanilla"), MultiTypePropertyValue("Strawberry"), MultiTypePropertyValue("Chocolate")))
    val asset1 = MultiValue(Seq(choice1, ratings))
    val asset2 = MultiValue(Seq(ratings, choice2))
    asset1.equals(asset2) should be (true)
  }
}