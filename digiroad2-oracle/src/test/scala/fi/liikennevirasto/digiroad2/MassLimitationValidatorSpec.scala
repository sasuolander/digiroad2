//package fi.liikennevirasto.digiroad2
//
//import fi.liikennevirasto.digiroad2.asset.LinkGeomSource.NormalLinkInterface
//import fi.liikennevirasto.digiroad2.asset._
//import fi.liikennevirasto.digiroad2.client.vvh.VVHClient
//import fi.liikennevirasto.digiroad2.dao.linearasset.OracleLinearAssetDao
//import fi.liikennevirasto.digiroad2.dao.pointasset.PersistedTrafficSign
//import fi.liikennevirasto.digiroad2.linearasset.{NumericValue, PersistedLinearAsset}
//import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
//import fi.liikennevirasto.digiroad2.process._
//import fi.liikennevirasto.digiroad2.service.RoadLinkService
//import fi.liikennevirasto.digiroad2.service.linearasset.LinearAssetTypes
//import fi.liikennevirasto.digiroad2.service.pointasset.{TrafficSignService, TrafficSignType}
//import slick.driver.JdbcDriver.backend.Database
//import Database.dynamicSession
//import org.mockito.Mockito._
//import org.scalatest.mockito.MockitoSugar
//import org.scalatest.{FunSuite, Matchers}
//
//class MassLimitationValidatorSpec  extends FunSuite with Matchers {
//  val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
//  val mockVVHClient = MockitoSugar.mock[VVHClient]
//  val mockTrafficSignService = MockitoSugar.mock[TrafficSignService]
//  val mockLinearAssetDao: OracleLinearAssetDao = MockitoSugar.mock[OracleLinearAssetDao]
//
//  case class MassLimitationInfoValidation(typeId: Int, service: MassLimitationValidator, trafficSign: TrafficSignType )
//  class TestWidthLimitValidator extends WidthLimitValidator {
//    override lazy val dao: OracleLinearAssetDao = mockLinearAssetDao
//    override lazy val roadLinkService: RoadLinkService = mockRoadLinkService
//    override lazy val vvhClient: VVHClient = mockVVHClient
//  }
//
//  class TestHeightLimitValidator extends HeightLimitValidator {
//    override lazy val dao: OracleLinearAssetDao = mockLinearAssetDao
//    override lazy val roadLinkService: RoadLinkService = mockRoadLinkService
//    override lazy val vvhClient: VVHClient = mockVVHClient
//  }
//
//  class TestTotalWeightLimitValidator extends TotalWeightLimitValidator {
//    override lazy val dao: OracleLinearAssetDao = mockLinearAssetDao
//    override lazy val roadLinkService: RoadLinkService = mockRoadLinkService
//    override lazy val vvhClient: VVHClient = mockVVHClient
//  }
//
//  class TestTrailerTruckWeightLimitValidator extends TrailerTruckWeightLimitValidator {
//    override lazy val dao: OracleLinearAssetDao = mockLinearAssetDao
//    override lazy val roadLinkService: RoadLinkService = mockRoadLinkService
//    override lazy val vvhClient: VVHClient = mockVVHClient
//  }
//
//  class TestAxleWeightLimitValidator extends AxleWeightLimitValidator {
//    override lazy val dao: OracleLinearAssetDao = mockLinearAssetDao
//    override lazy val roadLinkService: RoadLinkService = mockRoadLinkService
//    override lazy val vvhClient: VVHClient = mockVVHClient
//  }
//
//  class TestBogieWeightLimitValidator extends BogieWeightLimitValidator {
//    override lazy val dao: OracleLinearAssetDao = mockLinearAssetDao
//    override lazy val roadLinkService: RoadLinkService = mockRoadLinkService
//    override lazy val vvhClient: VVHClient = mockVVHClient
//  }
//
//  class TestLengthLimitValidator extends LengthLimitValidator {
//    override lazy val dao: OracleLinearAssetDao = mockLinearAssetDao
//    override lazy val roadLinkService: RoadLinkService = mockRoadLinkService
//    override lazy val vvhClient: VVHClient = mockVVHClient
//  }
//
//  val widthLimitValidator = new TestWidthLimitValidator
//  val heightLimitValidator = new TestHeightLimitValidator
//  val totalWeightLimitValidator = new TestTotalWeightLimitValidator
//  val trailerTruckWeightLimitValidator = new TestTrailerTruckWeightLimitValidator
//  val axleWeightLimitValidator = new TestAxleWeightLimitValidator
//  val bogieWeightLimitValidator = new TestBogieWeightLimitValidator
//  val lengthLimitValidator = new TestLengthLimitValidator
//  val massLimitationAssets = Seq(
//    MassLimitationInfoValidation(WidthLimit.typeId, widthLimitValidator, TrafficSignType.FreeWidth),
//    MassLimitationInfoValidation(HeightLimit.typeId, heightLimitValidator, TrafficSignType.MaxHeightExceeding),
//    MassLimitationInfoValidation(TotalWeightLimit.typeId, totalWeightLimitValidator, TrafficSignType.MaxLadenExceeding),
//    MassLimitationInfoValidation(TrailerTruckWeightLimit.typeId, trailerTruckWeightLimitValidator,TrafficSignType.MaxMassCombineVehiclesExceeding),
//    MassLimitationInfoValidation(AxleWeightLimit.typeId, axleWeightLimitValidator, TrafficSignType.MaxTonsOneAxleExceeding),
//    MassLimitationInfoValidation(BogieWeightLimit.typeId, bogieWeightLimitValidator, TrafficSignType.MaxTonsOnBogieExceeding),
//    MassLimitationInfoValidation(LengthLimit.typeId, lengthLimitValidator, TrafficSignType.MaximumLength)
//  )
//
//  def massLimitationWithoutMatchedAssset(massLimitationAsset: MassLimitationInfoValidation): Unit = {
//    OracleDatabase.withDynTransaction {
//
//      val propTrafficSign = Seq(
//        Property(0, "trafficSigns_type", "", false, Seq(PropertyValue(massLimitationAsset.trafficSign.value.toString))),
//        Property(1, "trafficSigns_value", "", false, Seq(PropertyValue("100"))),
//        Property(2, "trafficSigns_info", "", false, Seq(PropertyValue("200"))))
//
//      val trafficSign = PersistedTrafficSign(1, 1002l, 2, 2, 2, false, 0, 235, propTrafficSign, None, None, None, None, SideCode.AgainstDigitizing.value, None, NormalLinkInterface)
//
//      when(massLimitationAsset.service.dao.fetchLinearAssetsByLinkIds(widthLimitValidator.assetTypeInfo.typeId, Seq(1003l), LinearAssetTypes.numericValuePropertyId, false)).thenReturn(Seq())
//      when(massLimitationAsset.service.dao.fetchLinearAssetsByLinkIds(widthLimitValidator.assetTypeInfo.typeId, Seq(1004l), LinearAssetTypes.numericValuePropertyId, false))
//        .thenReturn(Seq(PersistedLinearAsset(1, 1, 1, Some(NumericValue(100)), 0.4, 9.6, None, None, None, None, false, massLimitationAsset.typeId, 0, None, LinkGeomSource.NormalLinkInterface, None, None, None)))
//
//      val result = widthLimitValidator.assetValidator(trafficSign)
//      withClue("assetName " + AssetTypeInfo.apply(massLimitationAsset.typeId).toString) {
//        result should be(false)
//      }
//
//      dynamicSession.rollback()
//    }
//  }
//
//  def massLimitationWithtMatchedAssset(massLimitationAsset: MassLimitationInfoValidation): Unit = {
//    OracleDatabase.withDynTransaction {
//      val propTrafficSign = Seq(
//        Property(0, "trafficSigns_type", "", false, Seq(PropertyValue(massLimitationAsset.trafficSign.value.toString))),
//        Property(1, "trafficSigns_value", "", false, Seq()),
//        Property(2, "trafficSigns_info", "", false, Seq(PropertyValue("200"))))
//
//      val trafficSign = PersistedTrafficSign(1, 1002l, 2, 2, 2, false, 0, 235, propTrafficSign, None, None, None, None, SideCode.AgainstDigitizing.value, None, NormalLinkInterface)
//
//      when(massLimitationAsset.service.dao.fetchLinearAssetsByLinkIds(widthLimitValidator.assetTypeInfo.typeId, Seq(1003l), LinearAssetTypes.numericValuePropertyId, false)).thenReturn(Seq())
//      when(massLimitationAsset.service.dao.fetchLinearAssetsByLinkIds(widthLimitValidator.assetTypeInfo.typeId, Seq(1004l), LinearAssetTypes.numericValuePropertyId, false))
//        .thenReturn(Seq(PersistedLinearAsset(1, 1, 1, Some(NumericValue(200)), 0.4, 9.6, None, None, None, None, false, massLimitationAsset.typeId, 0, None, LinkGeomSource.NormalLinkInterface, None, None, None)))
//
//      val result = widthLimitValidator.assetValidator(trafficSign)
//      withClue("assetName " + AssetTypeInfo.apply(massLimitationAsset.typeId).toString) {
//        result should be(true)
//      }
//
//      dynamicSession.rollback()
//    }
//  }
//
//  test("massLimitation traffic sign without match asset") {
//    massLimitationAssets.foreach { massLimitationAsset =>
//      massLimitationWithoutMatchedAssset(massLimitationAsset)
//    }
//  }
//
//  test("widthLimit traffic sign should find a match asset") {
//    massLimitationAssets.foreach { massLimitationAsset =>
//      massLimitationWithtMatchedAssset(massLimitationAsset)
//    }
//  }
//}