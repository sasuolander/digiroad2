package fi.liikennevirasto.digiroad2.service.pointasset

import java.text.SimpleDateFormat
import java.util.Date

import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.tierekisteri._
import fi.liikennevirasto.digiroad2.client.vvh.{FeatureClass, VVHClient, VVHRoadLinkClient, VVHRoadlink}
import fi.liikennevirasto.digiroad2.dao.{MassTransitStopDao, MunicipalityDao, MunicipalityInfo, Sequences}
import fi.liikennevirasto.digiroad2.dao.{RoadAddress => ViiteRoadAddress}
import fi.liikennevirasto.digiroad2.linearasset.RoadLink
import fi.liikennevirasto.digiroad2.service.{RoadAddressService, RoadLinkService}
import fi.liikennevirasto.digiroad2.service.pointasset.masstransitstop._
import fi.liikennevirasto.digiroad2.user.{Configuration, User}
import fi.liikennevirasto.digiroad2.util._
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.mockito.{ArgumentCaptor, Mockito}
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{BeforeAndAfter, FunSuite, Matchers}
import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.StaticQuery.interpolation
import slick.jdbc.{StaticQuery => Q}

class MassTransitStopServiceSpec extends FunSuite with Matchers with BeforeAndAfter {
  val mockRoadAddressService = MockitoSugar.mock[RoadAddressService]
  val geometryTransform = new GeometryTransform(mockRoadAddressService)
  val boundingBoxWithKauniainenAssets = BoundingRectangle(Point(374000,6677000), Point(374800,6677600))
  val userWithKauniainenAuthorization = User(
    id = 1,
    username = "Hannu",
    configuration = Configuration(authorizedMunicipalities = Set(235)))
  val mockTierekisteriClient = MockitoSugar.mock[TierekisteriMassTransitStopClient]
  when(mockTierekisteriClient.fetchMassTransitStop(any[String])).thenReturn(Some(
    TierekisteriMassTransitStop(2, "2", RoadAddress(None, 1, 1, Track.Combined, 1), TRRoadSide.Unknown, StopType.Combined,
      false, equipments = Map(), None, None, None, "KX12356", None, None, None, new Date))
  )

  val vvhRoadLinks = List(
    VVHRoadlink(1611353, 90, Nil, Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers),
    VVHRoadlink(1021227, 90, Nil, Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers),
    VVHRoadlink(1021226, 90, Nil, Private, TrafficDirection.AgainstDigitizing, FeatureClass.AllOthers),
    VVHRoadlink(123l, 91, List(Point(0.0,0.0), Point(120.0, 0.0)), Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers),
    VVHRoadlink(12333l, 91, List(Point(0.0,0.0), Point(120.0, 0.0)), State, TrafficDirection.BothDirections, FeatureClass.AllOthers),
    VVHRoadlink(131573L, 235, List(Point(0.0,0.0), Point(120.0, 0.0)), Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers),
    VVHRoadlink(6488445, 235, List(Point(0.0,0.0), Point(120.0, 0.0)), Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers),
    VVHRoadlink(1611353, 235, Seq(Point(374603.57,6677262.009), Point(374684.567, 6677277.323)), Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers),
    VVHRoadlink(1611341l, 91, Seq(Point(374375.156,6677244.904), Point(374567.632, 6677255.6)), Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers),
    VVHRoadlink(1l, 235, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers),
    VVHRoadlink(1611601L, 235, Seq(Point(374668.195,6676884.282), Point(374805.498, 6676906.051)), Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers),
    VVHRoadlink(1237l, 91, List(Point(0.0,0.0), Point(120.0, 0.0)), Municipality, TrafficDirection.AgainstDigitizing, FeatureClass.AllOthers),
    VVHRoadlink(12345l, 91, List(Point(0.0,0.0), Point(120.0, 0.0)), Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers, attributes = Map("ROADNAME_SE" -> "roadname_se",
      "ROADNAME_FI" -> "roadname_fi")))

  val mockVVHClient = MockitoSugar.mock[VVHClient]
  val mockVVHRoadLinkClient = MockitoSugar.mock[VVHRoadLinkClient]
  val mockGeometryTransform = MockitoSugar.mock[GeometryTransform]
  val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]

  def toRoadLink(l: VVHRoadlink) = {
    RoadLink(l.linkId, l.geometry, GeometryUtils.geometryLength(l.geometry),
      l.administrativeClass, 1, l.trafficDirection, UnknownLinkType, None, None, l.attributes + ("MUNICIPALITYCODE" -> BigInt(l.municipalityCode)))
  }
  before {
    // Reset the mocks here so individual tests don't have to
    when(mockVVHClient.roadLinkData).thenReturn(mockVVHRoadLinkClient)
    when(mockVVHRoadLinkClient.fetchByMunicipalitiesAndBounds(any[BoundingRectangle], any[Set[Int]])).thenReturn(vvhRoadLinks)
    vvhRoadLinks.foreach(rl =>
      when(mockVVHRoadLinkClient.fetchByLinkId(rl.linkId))
        .thenReturn(Some(rl)))
    when(mockRoadLinkService.getRoadLinksWithComplementaryFromVVH(any[BoundingRectangle], any[Set[Int]], any[Boolean])).thenReturn(vvhRoadLinks.map(toRoadLink))
    vvhRoadLinks.foreach(rl =>
      when(mockRoadLinkService.getRoadLinkByLinkIdFromVVH(rl.linkId, false))
        .thenReturn(Some(toRoadLink(rl))))
    when(mockRoadLinkService.getRoadLinksByLinkIdsFromVVH(any[Set[Long]], any[Boolean])).thenReturn(vvhRoadLinks.map(toRoadLink))
    when(mockRoadLinkService.getRoadLinkAndComplementaryFromVVH(any[Long], any[Boolean])).thenReturn(Some(toRoadLink(VVHRoadlink(1611601L, 91, Seq(Point(374668.195,6676884.282), Point(374805.498, 6676906.051)), State, TrafficDirection.BothDirections, FeatureClass.AllOthers))))
    when(mockRoadLinkService.getHistoryDataLinkFromVVH(any[Long], any[Boolean])).thenReturn(None)

  }
  val mockEventBus = MockitoSugar.mock[DigiroadEventBus]
  object RollbackBusStopStrategy extends BusStopStrategy(10, new MassTransitStopDao, mockRoadLinkService, mockEventBus, mockGeometryTransform)
  {
    def updateAdministrativeClassValueTest(assetId: Long, administrativeClass: AdministrativeClass): Unit ={
      super.updateAdministrativeClassValue(assetId, administrativeClass)
    }
  }

  class TestMassTransitStopService(val eventbus: DigiroadEventBus, val roadLinkService: RoadLinkService) extends MassTransitStopService {
    override def withDynSession[T](f: => T): T = f
    override def withDynTransaction[T](f: => T): T = f
    override val tierekisteriClient: TierekisteriMassTransitStopClient = mockTierekisteriClient
    override val massTransitStopDao: MassTransitStopDao = new MassTransitStopDao
    override val municipalityDao: MunicipalityDao = new MunicipalityDao
    override val geometryTransform: GeometryTransform = mockGeometryTransform
  }

  class TestMassTransitStopServiceWithTierekisteri(val eventbus: DigiroadEventBus, val roadLinkService: RoadLinkService) extends MassTransitStopService {
    override def withDynSession[T](f: => T): T = f
    override def withDynTransaction[T](f: => T): T = f
    override val tierekisteriClient: TierekisteriMassTransitStopClient = mockTierekisteriClient
    override val massTransitStopDao: MassTransitStopDao = new MassTransitStopDao
    override val municipalityDao: MunicipalityDao = new MunicipalityDao
    override val geometryTransform: GeometryTransform = mockGeometryTransform
  }

  object RollbackMassTransitStopService extends TestMassTransitStopService(new DummyEventBus, mockRoadLinkService)

  object RollbackMassTransitStopServiceWithTierekisteri extends TestMassTransitStopServiceWithTierekisteri(new DummyEventBus, mockRoadLinkService)

  class TestMassTransitStopServiceWithDynTransaction(val eventbus: DigiroadEventBus, val roadLinkService: RoadLinkService) extends MassTransitStopService {
    override def withDynSession[T](f: => T): T = TestTransactions.withDynSession()(f)
    override def withDynTransaction[T](f: => T): T = TestTransactions.withDynTransaction()(f)
    override val tierekisteriClient: TierekisteriMassTransitStopClient = mockTierekisteriClient
    override val massTransitStopDao: MassTransitStopDao = new MassTransitStopDao
    override val municipalityDao: MunicipalityDao = new MunicipalityDao
    override val geometryTransform: GeometryTransform = mockGeometryTransform
  }

  class MassTransitStopServiceWithTierekisteri(val eventbus: DigiroadEventBus, val roadLinkService: RoadLinkService) extends MassTransitStopService {
    override val tierekisteriClient: TierekisteriMassTransitStopClient = mockTierekisteriClient
    override val massTransitStopDao: MassTransitStopDao = new MassTransitStopDao
    override val municipalityDao: MunicipalityDao = new MunicipalityDao
    override val geometryTransform: GeometryTransform = mockGeometryTransform
  }

  def runWithRollback(test: => Unit): Unit = assetLock.synchronized {
    TestTransactions.runWithRollback()(test)
  }

  val assetLock = "Used to prevent deadlocks"

  test("create and update inventory date propertie") {

    val props = Seq(SimplePointAssetProperty("foo", Seq()))
    val roadLink = VVHRoadlink(12345l, 91, List(Point(0.0,0.0), Point(120.0, 0.0)), Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)

    val after = MassTransitStopOperations.setPropertiesDefaultValues(props, roadLink)
    after should have size (4)
    after.filter(_.publicId == MassTransitStopOperations.InventoryDateId ) should have size (1)
    val after2 = MassTransitStopOperations.setPropertiesDefaultValues(after, roadLink)
    after2 should have size (4)
  }

  test("update empty inventory date") {
    val roadLink = VVHRoadlink(12345l, 91, List(Point(0.0,0.0), Point(120.0, 0.0)), Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)
    val props = Seq(SimplePointAssetProperty(MassTransitStopOperations.InventoryDateId, Seq()))
    val after = MassTransitStopOperations.setPropertiesDefaultValues(props, roadLink)
    after should have size (3)
    after.filter(_.publicId == MassTransitStopOperations.InventoryDateId ) should have size(1)
    after.filter(_.publicId == MassTransitStopOperations.InventoryDateId ).head.values.head.asInstanceOf[PropertyValue].propertyValue should be ( DateTimeFormat.forPattern("yyyy-MM-dd").print(DateTime.now))
  }

  test("do not update existing inventory date") {
    val roadLink = VVHRoadlink(12345l, 91, List(Point(0.0,0.0), Point(120.0, 0.0)), Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)
    val props = Seq(SimplePointAssetProperty(MassTransitStopOperations.InventoryDateId, Seq(PropertyValue("2015-12-30"))))
    val after = MassTransitStopOperations.setPropertiesDefaultValues(props, roadLink)
    after should have size (3)
    after.filter(_.publicId == MassTransitStopOperations.InventoryDateId ) should have size(1)
    after.filter(_.publicId == MassTransitStopOperations.InventoryDateId ).head.values should have size(1)
    after.filter(_.publicId == MassTransitStopOperations.InventoryDateId ).head.values.head.asInstanceOf[PropertyValue].propertyValue should be ( "2015-12-30")
  }

  test("Calculate mass transit stop validity periods") {
    runWithRollback {
      val massTransitStops = RollbackMassTransitStopService.getByBoundingBox(userWithKauniainenAuthorization, boundingBoxWithKauniainenAssets)
      massTransitStops.find(_.id == 300000).flatMap(_.validityPeriod) should be(Some(MassTransitStopValidityPeriod.Current))
      massTransitStops.find(_.id == 300001).flatMap(_.validityPeriod) should be(Some(MassTransitStopValidityPeriod.Past))
      massTransitStops.find(_.id == 300003).flatMap(_.validityPeriod) should be(Some(MassTransitStopValidityPeriod.Future))
    }
  }

  test("Return mass transit stop types") {
    runWithRollback {
      val massTransitStops = RollbackMassTransitStopService.getByBoundingBox(userWithKauniainenAuthorization, boundingBoxWithKauniainenAssets)
      massTransitStops.find(_.id == 300000).get.stopTypes should be(Seq(2))
      massTransitStops.find(_.id == 300001).get.stopTypes.sorted should be(Seq(2, 3, 4))
      massTransitStops.find(_.id == 300003).get.stopTypes.sorted should be(Seq(2, 3))
    }
  }

  test("Get stops by bounding box") {
    runWithRollback {
      val roadLink = RoadLink(11, List(Point(0.0,0.0), Point(120.0, 0.0)), 120, Municipality, 1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val id = RollbackMassTransitStopService.create(NewMassTransitStop(5.0, 0.0, 1l, 2,
        Seq(
          SimplePointAssetProperty("tietojen_yllapitaja", Seq(PropertyValue("1"))),
          SimplePointAssetProperty("pysakin_tyyppi", Seq(PropertyValue("2"))),
          SimplePointAssetProperty("vaikutussuunta", Seq(PropertyValue("2")))
        )), "masstransitstopservice_spec", roadLink)
      val stops = RollbackMassTransitStopService.getByBoundingBox(
        userWithKauniainenAuthorization, BoundingRectangle(Point(0.0, 0.0), Point(10.0, 10.0)))
      stops.map(_.id) should be(Seq(id))
    }
  }

  test("Stops should not be filtered by authorization") {
    runWithRollback {
      val stops = RollbackMassTransitStopService.getByBoundingBox(User(0, "test", Configuration()), boundingBoxWithKauniainenAssets)
      stops should not be(empty)
    }
  }

  test("Stop floats if road link does not exist") {
    runWithRollback {
      val stops = RollbackMassTransitStopService.getByBoundingBox(userWithKauniainenAuthorization, boundingBoxWithKauniainenAssets)
      stops.find(_.id == 300000).map(_.floating) should be(Some(true))
    }
  }

  test("Stop floats if stop and roadlink municipality codes differ") {
    runWithRollback {
      val stops = RollbackMassTransitStopService.getByBoundingBox(userWithKauniainenAuthorization, boundingBoxWithKauniainenAssets)
      stops.find(_.id == 300004).map(_.floating) should be(Some(true))
    }
  }

  test("Stop floats if stop is too far from linearly referenced location") {
    runWithRollback {
      val stops = RollbackMassTransitStopService.getByBoundingBox(userWithKauniainenAuthorization, boundingBoxWithKauniainenAssets)
      stops.find(_.id == 300008).map(_.floating) should be(Some(true))
    }
  }

  test("Persist mass transit stop floating status change") {
    runWithRollback {
      RollbackMassTransitStopService.getByBoundingBox(userWithKauniainenAuthorization, boundingBoxWithKauniainenAssets)
      val floating: Option[Boolean] = sql"""select floating from asset where id = 300008""".as[Boolean].firstOption
      floating should be(Some(true))
    }
  }

  test("Persist mass transit stop floating direction not match") {
    runWithRollback {
    val massTransitStopDao = new MassTransitStopDao
    val roadLink = RoadLink(1237l, List(Point(0.0,0.0), Point(120.0, 0.0)), 120, Municipality, 1, TrafficDirection.TowardsDigitizing, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
    val id = RollbackMassTransitStopService.create(NewMassTransitStop(5.0, 0.0, 1237l, 2,
      Seq(
        SimplePointAssetProperty("tietojen_yllapitaja", Seq(PropertyValue("1"))),
        SimplePointAssetProperty("pysakin_tyyppi", Seq(PropertyValue("2"))),
        SimplePointAssetProperty("vaikutussuunta", Seq(PropertyValue("2"))) //TowardsDigitizin
      )), "masstransitstopservice_spec", roadLink)

      val stops = RollbackMassTransitStopService.getByBoundingBox(
      userWithKauniainenAuthorization, BoundingRectangle(Point(0.0, 0.0), Point(10.0, 10.0)))
      stops.find(_.id == id).map(_.floating) should be(Some(true))
      massTransitStopDao.getAssetFloatingReason(id) should be(Some(FloatingReason.TrafficDirectionNotMatch))
    }
  }

  test("Get properties") {
    when(mockGeometryTransform.resolveAddressAndLocation(Point(374675.043988335, 6677274.14596169), 69, 109.0, 1611353, 2, road = None)).thenReturn((RoadAddress(Some("1"),1,1,Track(1),1),RoadSide(1)))
    runWithRollback {
      val massTransitStop = RollbackMassTransitStopService.getMassTransitStopByNationalIdWithTRWarnings(2)._1.map { stop =>
        Map("id" -> stop.id,
          "nationalId" -> stop.nationalId,
          "stopTypes" -> stop.stopTypes,
          "lat" -> stop.lat,
          "lon" -> stop.lon,
          "validityDirection" -> stop.validityDirection,
          "bearing" -> stop.bearing,
          "validityPeriod" -> stop.validityPeriod,
          "floating" -> stop.floating,
          "propertyData" -> stop.propertyData)
      }
    }
  }

  test("Fetching mass transit stop with id requires no rights") {
    when(mockGeometryTransform.resolveAddressAndLocation(Point(374780.259160265, 6677546.84962279), 30, 113.0, 6488445, 3, road = None)).thenReturn((RoadAddress(Some("1"),1,1,Track(1),1),RoadSide(1)))
    runWithRollback {
      val stop =  RollbackMassTransitStopService.getMassTransitStopByNationalIdWithTRWarnings(85755)
      stop._1.get.id should be (300008)
    }
  }

  test("Update mass transit stop road link mml id") {
    runWithRollback {
      val geom = Point(374450, 6677250)
      val position = Some(Position(geom.x, geom.y, 1611601L, Some(85)))
      RollbackMassTransitStopService.updateExistingById(300000, position, Set.empty, "user", (_,_) => Unit)
      val linkId = sql"""
            select lrm.link_id from asset a
            join asset_link al on al.asset_id = a.id
            join lrm_position lrm on lrm.id = al.position_id
            where a.id = 300000
      """.as[Long].firstOption
      linkId should be(Some(1611601L))
    }
  }

  test("Update mass transit stop bearing") {
    runWithRollback {
      val geom = Point(374450, 6677250)
      val position = Some(Position(geom.x, geom.y, 1611341l, Some(90)))
      RollbackMassTransitStopService.updateExistingById(300000, position, Set.empty, "user", (_,_) => Unit)
      val bearing = sql"""
            select a.bearing from asset a
            join asset_link al on al.asset_id = a.id
            join lrm_position lrm on lrm.id = al.position_id
            where a.id = 300000
      """.as[Option[Int]].first
      bearing should be(Some(90))
    }
  }

  test("Update mass transit stop municipality") {
    runWithRollback {
      val geom = Point(374450, 6677250)
      val position = Some(Position(geom.x, geom.y, 1611341l, Some(85)))
      RollbackMassTransitStopService.updateExistingById(300000, position, Set.empty, "user", (_,_) => Unit)
      val municipality = sql"""
            select a.municipality_code from asset a
            join asset_link al on al.asset_id = a.id
            join lrm_position lrm on lrm.id = al.position_id
            where a.id = 300000
      """.as[Int].firstOption
      municipality should be(Some(91))
    }
  }

  test("Do not overwrite asset liVi identifier property when already administered by ELY"){
    runWithRollback {
      val eventbus = MockitoSugar.mock[DigiroadEventBus]
      val service = new TestMassTransitStopService(eventbus, mockRoadLinkService)
      val assetId = 300000
      sqlu"""update text_property_value set value_fi='livi1' where asset_id = 300000 and value_fi = 'OTHJ1'""".execute
      val dbResult = sql"""SELECT value_fi FROM text_property_value where value_fi='livi1' and asset_id = 300000""".as[String].list
      dbResult.size should be(1)
      dbResult.head should be("livi1")
      val properties = List(
        SimplePointAssetProperty("tietojen_yllapitaja", List(PropertyValue("2"))),
        SimplePointAssetProperty("yllapitajan_koodi", List(PropertyValue("OTHJ1"))))
      val position = Some(Position(374450, 6677250, 123l, None))
      RollbackMassTransitStopService.updateExistingById(assetId, position, properties.toSet, "user", (_,_) => Unit)
      val massTransitStop = service.getById(assetId).get

      //The property yllapitajan_koodi should be overridden with OTHJ + NATIONAL ID
      val liviIdentifierProperty = massTransitStop.propertyData.find(p => p.publicId == "yllapitajan_koodi").get
      liviIdentifierProperty.values.head.asInstanceOf[PropertyValue].propertyValue should be("livi1")
    }
  }

  test("Do not overwrite LiviId of ELY/HSL stops when Tietojen ylläpitäjä is empty in csv import file") {
    runWithRollback {
      val eventbus = MockitoSugar.mock[DigiroadEventBus]
      val service = new TestMassTransitStopService(eventbus, mockRoadLinkService)
      val assetId = 300000
      // properties in csv import file: 1;;Swedish name;;;;;;;;;;;;;;; (national id and swedish name given)
      val properties = List(
        SimplePointAssetProperty("nimi_ruotsiksi", List(PropertyValue("Swedish name"))))
      RollbackMassTransitStopService.updateExistingById(assetId, None, properties.toSet, "user", (_,_) => Unit)
      val massTransitStop = service.getById(assetId).get

      val swedishNameProperty = massTransitStop.propertyData.find(p => p.publicId == "nimi_ruotsiksi").get
      swedishNameProperty.values.head.asInstanceOf[PropertyValue].propertyValue should be("Swedish name")

      val liviIdentifierProperty = massTransitStop.propertyData.find(p => p.publicId == "yllapitajan_koodi").get
      liviIdentifierProperty.values.head.asInstanceOf[PropertyValue].propertyValue should be("OTHJ1")
    }
  }
  
  test("Update last modified info") {
    runWithRollback {
      val geom = Point(374450, 6677250)
      val pos = Position(geom.x, geom.y, 131573L, Some(85))
      RollbackMassTransitStopService.updateExistingById(300000, Some(pos), Set.empty, "user", (_,_) => Unit)
      val modifier = sql"""
            select a.modified_by from asset a
            where a.id = 300000
      """.as[String].firstOption
      modifier should be(Some("user"))
    }
  }

  test("Update properties") {
    runWithRollback {
      val values = List(PropertyValue("New name"))
      val properties = Set(SimplePointAssetProperty("nimi_suomeksi", values))
      RollbackMassTransitStopService.updateExistingById(300000, None, properties, "user", (_,_) => Unit)
      val modifier = sql"""
            select v.value_fi from text_property_value v
            join property p on v.property_id = p.id
            where v.asset_id = 300000 and p.public_id = 'nimi_suomeksi'
      """.as[String].firstOption
      modifier should be(Some("New name"))
    }
  }

  test("Persist floating on update") {
    // This asset is actually supposed to be floating, but updateExisting shouldn't do a floating check
    runWithRollback {
      val position = Some(Position(60.0, 0.0, 123l, None))
      sql"""
            update asset a
            set floating = '0'
            where a.id = 300002
      """.asUpdate.execute
      RollbackMassTransitStopService.updateExistingById(300002, position, Set.empty, "user", (_, _) => Unit)
      val floating = sql"""
            select a.floating from asset a
            where a.id = 300002
      """.as[Boolean].firstOption
      floating should be(Some(false))
    }
  }

  test("Send event to event bus in update") {
    runWithRollback {
      val eventbus = MockitoSugar.mock[DigiroadEventBus]
      val service = new TestMassTransitStopService(eventbus, mockRoadLinkService)
      val position = Some(Position(60.0, 0.0, 123l, None))
      service.updateExistingById(300002, position, Set.empty, "user", (_,_) => Unit)
      verify(eventbus).publish(org.mockito.ArgumentMatchers.eq("asset:saved"), any[EventBusMassTransitStop]())
    }
  }

  test("Assert user rights when updating a mass transit stop") {
    runWithRollback {
      val position = Some(Position(60.0, 0.0, 123l, None))
      an [Exception] should be thrownBy RollbackMassTransitStopService.updateExistingById(300002, position, Set.empty, "user", { (municipalityCode, _) => throw new Exception })
    }
  }

  test("Create new mass transit stop") {
    runWithRollback {
      val eventbus = MockitoSugar.mock[DigiroadEventBus]
      val service = new TestMassTransitStopService(eventbus, mockRoadLinkService)
      val properties = List(
        SimplePointAssetProperty("pysakin_tyyppi", List(PropertyValue("1"))),
        SimplePointAssetProperty("tietojen_yllapitaja", List(PropertyValue("1"))),
        SimplePointAssetProperty("yllapitajan_koodi", List(PropertyValue("livi"))),
        SimplePointAssetProperty("vaikutussuunta", List(PropertyValue("2"))))

      val roadLink = RoadLink(123l, List(Point(0.0,0.0), Point(120.0, 0.0)), 120, Municipality, 1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(91)))
      val id = service.create(NewMassTransitStop(60.0, 0.0, 123l, 100, properties), "test", roadLink)
      val massTransitStop = service.getById(id).get
      massTransitStop.bearing should be(Some(100))
      massTransitStop.floating should be(false)
      massTransitStop.stopTypes should be(List(1))
      massTransitStop.validityPeriod should be(Some(MassTransitStopValidityPeriod.Current))
      mockRoadLinkService.getRoadLinkAndComplementaryFromVVH(123l, newTransaction = false).get.linkSource.value should be (1)

      //The property yllapitajan_koodi should not have values
      val liviIdentifierProperty = massTransitStop.propertyData.find(p => p.publicId == "yllapitajan_koodi").get
      liviIdentifierProperty.values.size should be(0)

      verify(eventbus).publish(org.mockito.ArgumentMatchers.eq("asset:saved"), any[EventBusMassTransitStop]())
    }
  }

  test("Create new virtual mass transit stop with Central ELY administration") {
    runWithRollback {
      val massTransitStopDao = new MassTransitStopDao
      val eventbus = MockitoSugar.mock[DigiroadEventBus]
      val service = new TestMassTransitStopService(eventbus, mockRoadLinkService)
      val properties = List(
        SimplePointAssetProperty("pysakin_tyyppi", List(PropertyValue("5"))),
        SimplePointAssetProperty("tietojen_yllapitaja", List(PropertyValue("2"))),
        SimplePointAssetProperty("yllapitajan_koodi", List(PropertyValue("livi"))),
        SimplePointAssetProperty("vaikutussuunta", List(PropertyValue("2"))))
      val roadLink = RoadLink(123l, List(Point(0.0,0.0), Point(120.0, 0.0)), 120, Municipality, 1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(91)))
      val id = service.create(NewMassTransitStop(60.0, 0.0, 123l, 100, properties), "test", roadLink)
      val massTransitStop = service.getById(id).get
      massTransitStop.bearing should be(Some(100))
      massTransitStop.floating should be(false)
      massTransitStop.stopTypes should be(List(5))
      massTransitStop.validityPeriod should be(Some(MassTransitStopValidityPeriod.Current))

      //The property yllapitajan_koodi should not have values
      val liviIdentifierProperty = massTransitStop.propertyData.find(p => p.publicId == "yllapitajan_koodi").get
      liviIdentifierProperty.values.size should be(0)

      verify(eventbus).publish(org.mockito.ArgumentMatchers.eq("asset:saved"), any[EventBusMassTransitStop]())
    }
  }

  test ("Convert PersistedMassTransitStop into TierekisteriMassTransitStop") {
    val dateFormatter = new SimpleDateFormat("yyyy-MM-dd")
    runWithRollback {

      val dummyRoadAddress = Some(ViiteRoadAddress(1, 921, 2, Track.RightSide, 0, 299, None, None, 1641830, 0, 298.694, SideCode.BothDirections, Seq(Point(4002, 3067), Point(385258.765,7300119.103)), false, None, None, None))

      when(mockRoadAddressService.getByLrmPosition(any[Long], any[Double])).thenReturn(dummyRoadAddress)

      val assetId = 300006
      val stopOption = RollbackMassTransitStopService.fetchPointAssets((s:String) => s"""$s where a.id = $assetId""").headOption
      stopOption.isEmpty should be (false)
      val stop = stopOption.get
      val (address, roadSide) = geometryTransform.resolveAddressAndLocation(Point(0,0), 0, stop.mValue, stop.linkId, stop.validityDirection.get, Some(stop.municipalityCode))
      val trStop = TierekisteriBusStopMarshaller.toTierekisteriMassTransitStop(stop, address, Option(roadSide))
      roadSide should be (RoadSide.Left)
      trStop.nationalId should be (stop.nationalId)
      trStop.stopType should be (StopType.LongDistance)
      trStop.equipments.get(Equipment.Roof).get should be (Existence.Yes)
      trStop.equipments.filterNot( x => x._1 == Equipment.Roof).forall(_._2 == Existence.Unknown) should be (true)
      trStop.operatingFrom.isEmpty should be (false)
      trStop.operatingTo.isEmpty should be (false)
      val opFrom = stop.propertyData.find(_.publicId=="ensimmainen_voimassaolopaiva").flatMap(_.values.headOption.map(_.asInstanceOf[PropertyValue].propertyValue))
      val opTo = stop.propertyData.find(_.publicId=="viimeinen_voimassaolopaiva").flatMap(_.values.headOption.map(_.asInstanceOf[PropertyValue].propertyValue))
      opFrom shouldNot be (None)
      opTo shouldNot be (None)
      dateFormatter.format(trStop.operatingFrom.get) should be (opFrom.get)
      dateFormatter.format(trStop.operatingTo.get) should be (opTo.get)
    }
  }

  test ("Test date conversions in Marshaller") {
    val dateFormatter = new SimpleDateFormat("yyyy-MM-dd")
    runWithRollback {

      val dummyRoadAddress = Some(ViiteRoadAddress(1, 921, 2, Track.RightSide, 0, 299, None, None, 1641830, 0, 298.694, SideCode.BothDirections, Seq(Point(4002, 3067), Point(385258.765,7300119.103)), false, None, None, None))

      when(mockRoadAddressService.getByLrmPosition(any[Long], any[Double])).thenReturn(dummyRoadAddress)

      val assetId = 300006
      val stopOption = RollbackMassTransitStopService.fetchPointAssets((s:String) => s"""$s where a.id = $assetId""").headOption
      stopOption.isEmpty should be (false)
      val stop = stopOption.get
      val (address, roadSide) = geometryTransform.resolveAddressAndLocation(Point(0,0), 0, stop.mValue, stop.linkId, stop.validityDirection.get, Some(stop.municipalityCode))
      val expireDate = new Date(10487450L)
      val trStop = TierekisteriBusStopMarshaller.toTierekisteriMassTransitStop(stop, address, Option(roadSide), Some(expireDate))
      trStop.operatingTo.isEmpty should be (false)
      trStop.operatingTo.get should be (expireDate)
      val trStopNoExpireDate = TierekisteriBusStopMarshaller.toTierekisteriMassTransitStop(stop, address, Option(roadSide), None)
      trStopNoExpireDate.operatingTo.isEmpty should be (false)
      trStopNoExpireDate.operatingTo.get shouldNot be (expireDate)
    }
  }

  test("Should not copy existing masstransitstop if the new distance is less or equal than 50 meters"){
    runWithRollback {
      val eventbus = MockitoSugar.mock[DigiroadEventBus]
      val service = new TestMassTransitStopService(eventbus, mockRoadLinkService)
      val properties = List(
        SimplePointAssetProperty("pysakin_tyyppi", List(PropertyValue("1"))),
        SimplePointAssetProperty("tietojen_yllapitaja", List(PropertyValue("2"))),
        SimplePointAssetProperty("yllapitajan_koodi", List(PropertyValue("livi"))),
        SimplePointAssetProperty("vaikutussuunta", List(PropertyValue("2"))))
      val linkId = 123l
      val municipalityCode = 91
      val geometry = Seq(Point(0.0,0.0), Point(120.0, 0.0))
      val roadLink = RoadLink(linkId, geometry, 120, Municipality, 1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(municipalityCode)))

      when(mockGeometryTransform.resolveAddressAndLocation(any[Point], any[Int], any[Double], any[Long], any[Int], any[Option[Int]], any[Option[Int]])).thenReturn(
        (RoadAddress(Some(municipalityCode.toString), 1, 1, Track.Combined, 0), RoadSide.Left)
      )

      val assetId = service.create(NewMassTransitStop(0, 0, linkId, 0, properties), "test", roadLink)
      val asset = sql"""select id, municipality_code, valid_from, valid_to from asset where id = $assetId""".as[(Long, Int, String, String)].firstOption
      asset should be (Some(assetId, municipalityCode, null, null))

      val updatedAssetId = service.updateExistingById(assetId, Some(Position(0, 50, linkId, Some(0))), Set(), "test",  (_, _) => Unit).id
      updatedAssetId should be(assetId)

      val expired = sql"""select case when a.valid_to <= current_timestamp then 1 else 0 end as expired from asset a where id = $assetId""".as[(Boolean)].firstOption
      expired should be(Some(false))
    }
  }
  
  test("Send Vallus message when deleting stop"){
    runWithRollback {
      val linkId = 123l
      val municipalityCode = 91
      val geometry = Seq(Point(0.0,0.0), Point(120.0, 0.0))
      val eventbus = MockitoSugar.mock[DigiroadEventBus]
      val service = new TestMassTransitStopServiceWithTierekisteri(eventbus, mockRoadLinkService)
      val properties = List(
        SimplePointAssetProperty("nimi_suomeksi", List(PropertyValue("value is copied"))),
        SimplePointAssetProperty("pysakin_tyyppi", List(PropertyValue("1"))),
        SimplePointAssetProperty("tietojen_yllapitaja", List(PropertyValue("2"))),
        SimplePointAssetProperty("vaikutussuunta", List(PropertyValue("2"))),
        SimplePointAssetProperty("ensimmainen_voimassaolopaiva", List(PropertyValue("2013-01-01")))
      )

      when(mockGeometryTransform.resolveAddressAndLocation(any[Point], any[Int], any[Double], any[Long], any[Int], any[Option[Int]], any[Option[Int]])).thenReturn(
        (RoadAddress(Some(municipalityCode.toString), 1, 1, Track.Combined, 0), RoadSide.Left)
      )
      val roadLink = RoadLink(linkId, geometry, 120, Municipality, 1, TrafficDirection.BothDirections,
        Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(municipalityCode)))
      val assetId = service.create(NewMassTransitStop(0, 0, linkId, 0, properties), "test", roadLink)
      service.deleteMassTransitStopData(assetId)
      verify(eventbus).publish(org.mockito.ArgumentMatchers.eq("asset:expired"), any[EventBusMassTransitStop]())
    }
  }

  test ("Get enumerated property values") {
    runWithRollback {
      val propertyValues = RollbackMassTransitStopService.massTransitStopEnumeratedPropertyValues
      propertyValues.nonEmpty should be (true)
      propertyValues.forall(x => x._2.nonEmpty) should be (true)
      propertyValues.forall(x => x._1 != "") should be (true)
    }
  }

  test("Stop floats if a State road has got changed to a road owned by municipality"){
    when(mockVVHRoadLinkClient.fetchByMunicipalitiesAndBounds(any[BoundingRectangle], any[Set[Int]])).thenReturn(vvhRoadLinks)

    val massTransitStopDao = new MassTransitStopDao
    runWithRollback{
      val assetId = 300006
      val boundingBox = BoundingRectangle(Point(370000,6077000), Point(374800,6677600))
      //Set administration class of the asset with State value
      RollbackBusStopStrategy.updateAdministrativeClassValueTest(assetId, State)
      val stops = RollbackMassTransitStopService.getByBoundingBox(userWithKauniainenAuthorization, boundingBox)
      stops.find(_.id == assetId).map(_.floating) should be(Some(true))
      massTransitStopDao.getAssetFloatingReason(assetId) should be(Some(FloatingReason.RoadOwnerChanged))
    }
  }

  test("Stop floats if a State road has got changed to a road owned to a private road"){
    when(mockVVHRoadLinkClient.fetchByMunicipalitiesAndBounds(any[BoundingRectangle], any[Set[Int]])).thenReturn(vvhRoadLinks)

    val massTransitStopDao = new MassTransitStopDao
    runWithRollback{
      val assetId = 300012
      val boundingBox = BoundingRectangle(Point(370000,6077000), Point(374800,6677600))
      //Set administration class of the asset with State value
      RollbackBusStopStrategy.updateAdministrativeClassValueTest(assetId, State)
      val stops = RollbackMassTransitStopService.getByBoundingBox(userWithKauniainenAuthorization, boundingBox)
      stops.find(_.id == assetId).map(_.floating) should be(Some(true))
      massTransitStopDao.getAssetFloatingReason(assetId) should be(Some(FloatingReason.RoadOwnerChanged))
    }
  }

  test("Stop floats if a Municipality road has got changed to a road owned by state"){
    val vvhRoadLinks = List(
      VVHRoadlink(1021227, 90, Nil, State, TrafficDirection.UnknownDirection, FeatureClass.AllOthers))

    when(mockVVHRoadLinkClient.fetchByMunicipalitiesAndBounds(any[BoundingRectangle], any[Set[Int]])).thenReturn(vvhRoadLinks)
    when(mockRoadLinkService.getRoadLinksWithComplementaryFromVVH(any[BoundingRectangle], any[Set[Int]], any[Boolean])).thenReturn(vvhRoadLinks.map(toRoadLink))

    val massTransitStopDao = new MassTransitStopDao
    runWithRollback{
      val assetId = 300006
      val boundingBox = BoundingRectangle(Point(370000,6077000), Point(374800,6677600))
      //Set administration class of the asset with State value
      RollbackBusStopStrategy.updateAdministrativeClassValueTest(assetId, Municipality)
      val stops = RollbackMassTransitStopService.getByBoundingBox(userWithKauniainenAuthorization, boundingBox)
      stops.find(_.id == assetId).map(_.floating) should be(Some(true))
      massTransitStopDao.getAssetFloatingReason(assetId) should be(Some(FloatingReason.RoadOwnerChanged))
    }
  }

  test("Stop floats if a Private road has got changed to a road owned by state"){
    val vvhRoadLinks = List(
      VVHRoadlink(1021227, 90, Nil, State, TrafficDirection.UnknownDirection, FeatureClass.AllOthers))

    when(mockVVHRoadLinkClient.fetchByMunicipalitiesAndBounds(any[BoundingRectangle], any[Set[Int]])).thenReturn(vvhRoadLinks)
    when(mockRoadLinkService.getRoadLinksWithComplementaryFromVVH(any[BoundingRectangle], any[Set[Int]], any[Boolean])).thenReturn(vvhRoadLinks.map(toRoadLink))

    val massTransitStopDao = new MassTransitStopDao
    runWithRollback{
      val assetId = 300006
      val boundingBox = BoundingRectangle(Point(370000,6077000), Point(374800,6677600))
      //Set administration class of the asset with State value
      RollbackBusStopStrategy.updateAdministrativeClassValueTest(assetId, Private)
      val stops = RollbackMassTransitStopService.getByBoundingBox(userWithKauniainenAuthorization, boundingBox)
      stops.find(_.id == assetId).map(_.floating) should be(Some(true))
      massTransitStopDao.getAssetFloatingReason(assetId) should be(Some(FloatingReason.RoadOwnerChanged))
    }
  }

  test("Stops working list shouldn't have floating assets with floating reason RoadOwnerChanged if user is not operator"){
    when(mockVVHRoadLinkClient.fetchByMunicipalitiesAndBounds(any[BoundingRectangle], any[Set[Int]])).thenReturn(vvhRoadLinks)

    runWithRollback {
      val assetId = 300012
      val boundingBox = BoundingRectangle(Point(370000,6077000), Point(374800,6677600))
      //Set administration class of the asset with State value
      RollbackBusStopStrategy.updateAdministrativeClassValueTest(assetId, State)
      //GetBoundingBox will set assets  to floating
      RollbackMassTransitStopService.getByBoundingBox(userWithKauniainenAuthorization, boundingBox)
      val workingList = RollbackMassTransitStopService.getFloatingAssetsWithReason(Some(Set(235)), Some(false))
      //Get all external ids from the working list
      val externalIds = workingList.map(m => m._2.map(a => a._2).flatten).flatten

      //Should not find any external id of the asset with administration class changed
      externalIds.foreach{ externalId =>
        externalId.get("id") should not be (Some(8))
      }
    }
  }

  test("Stops working list should have all floating assets if user is operator"){
    when(mockVVHRoadLinkClient.fetchByMunicipalitiesAndBounds(any[BoundingRectangle], any[Set[Int]])).thenReturn(vvhRoadLinks)

    runWithRollback {
      val assetId = 300012
      val boundingBox = BoundingRectangle(Point(370000,6077000), Point(374800,6677600))
      //Set administration class of the asset with State value
      RollbackBusStopStrategy.updateAdministrativeClassValueTest(assetId, State)
      //GetBoundingBox will set assets  to floating
      RollbackMassTransitStopService.getByBoundingBox(userWithKauniainenAuthorization, boundingBox)
      val workingList = RollbackMassTransitStopService.getFloatingAssetsWithReason(Some(Set(235)), Some(true))
      //Get all external ids from the working list
      val externalIds = workingList.map(m => m._2.map(a => a._2).flatten).flatten

      //Should have the external id of the asset with administration class changed
      externalIds.map(_.get("id")) should contain (Some(8))
    }
  }
  
  test("auto correct geometry with bounding box less than 3m") {
    runWithRollback {
      val roadLink = RoadLink(11, List(Point(0.0,0.0), Point(120.0, 0.0)), 120, Municipality, 1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val id = RollbackMassTransitStopService.create(NewMassTransitStop(5.0, 1.0, 1l, 2,
        Seq(
          SimplePointAssetProperty("tietojen_yllapitaja", Seq(PropertyValue("1"))),
          SimplePointAssetProperty("pysakin_tyyppi", Seq(PropertyValue("2"))),
          SimplePointAssetProperty("vaikutussuunta", Seq(PropertyValue("2")))
        )), "masstransitstopservice_spec", roadLink)
      val stops = RollbackMassTransitStopService.getByBoundingBox(
        userWithKauniainenAuthorization, BoundingRectangle(Point(0.0, 0.0), Point(10.0, 10.0)))
      val asset = stops.find(_.id == id)
      asset.get.lon should be(5.0)
      asset.get.lat should be(0.0)
    }
  }

    test("create and update roadNames properties") {
      val attributes: Map[String, Any] =
        Map("ROADNAME_SE" -> "roadname_se",
          "ROADNAME_FI" -> "roadname_fi")

      val props = Seq(SimplePointAssetProperty(MassTransitStopOperations.InventoryDateId, Seq(PropertyValue("2015-12-30"))))
      val roadLink = VVHRoadlink(12345l, 91, List(Point(0.0, 0.0), Point(120.0, 0.0)), Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers, attributes = attributes)

      val after = MassTransitStopOperations.setPropertiesDefaultValues(props, roadLink)
      after should have size (3)
      after.filter(_.publicId == MassTransitStopOperations.RoadName_FI) should have size (1)
      after.filter(_.publicId == MassTransitStopOperations.RoadName_SE) should have size (1)
      after.filter(_.publicId == MassTransitStopOperations.RoadName_FI).head.values.head.asInstanceOf[PropertyValue].propertyValue should be ("roadname_fi")
      after.filter(_.publicId == MassTransitStopOperations.RoadName_SE).head.values.head.asInstanceOf[PropertyValue].propertyValue should be ("roadname_se")
    }

  test("Update roadNames properties when exist and not filled") {
    val attributes: Map[String, Any] =
      Map("ROADNAME_SE" -> "roadname_se",
        "ROADNAME_FI" -> "roadname_fi")

    val props = Seq(SimplePointAssetProperty(MassTransitStopOperations.RoadName_SE, Seq.empty[PropertyValue]), SimplePointAssetProperty(MassTransitStopOperations.RoadName_FI, Seq.empty[PropertyValue]))
    val roadLink = VVHRoadlink(12345l, 91, List(Point(0.0, 0.0), Point(120.0, 0.0)), Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers, attributes = attributes)

    val after = MassTransitStopOperations.setPropertiesDefaultValues(props, roadLink)
    after should have size (3)
    after.filter(_.publicId == MassTransitStopOperations.RoadName_FI) should have size (1)
    after.filter(_.publicId == MassTransitStopOperations.RoadName_SE) should have size (1)
    after.filter(_.publicId == MassTransitStopOperations.RoadName_FI).head.values.head.asInstanceOf[PropertyValue].propertyValue should be ("roadname_fi")
    after.filter(_.publicId == MassTransitStopOperations.RoadName_SE).head.values.head.asInstanceOf[PropertyValue].propertyValue should be ("roadname_se")
  }

  test("Not update when roadNames properties are filled") {
    val attributes: Map[String, Any] =
      Map("ROADNAME_SE" -> "roadname_se",
        "ROADNAME_FI" -> "roadname_fi")

    val props = Seq(SimplePointAssetProperty(MassTransitStopOperations.RoadName_SE, Seq(PropertyValue("user_road_name_se"))),
                    SimplePointAssetProperty(MassTransitStopOperations.RoadName_FI, Seq(PropertyValue("user_road_name_fi"))))
    val roadLink = VVHRoadlink(12345l, 91, List(Point(0.0, 0.0), Point(120.0, 0.0)), Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers, attributes = attributes)

    val after = MassTransitStopOperations.setPropertiesDefaultValues(props, roadLink)
    after should have size (3)
    after.filter(_.publicId == MassTransitStopOperations.RoadName_FI) should have size (1)
    after.filter(_.publicId == MassTransitStopOperations.RoadName_SE) should have size (1)
    after.filter(_.publicId == MassTransitStopOperations.RoadName_FI).head.values.head.asInstanceOf[PropertyValue].propertyValue should be ("user_road_name_fi")
    after.filter(_.publicId == MassTransitStopOperations.RoadName_SE).head.values.head.asInstanceOf[PropertyValue].propertyValue should be ("user_road_name_se")
  }

  test("Find more than one busStop with same passengerId"){
    runWithRollback {
      val eventbus = MockitoSugar.mock[DigiroadEventBus]
      val service = new TestMassTransitStopService(eventbus, mockRoadLinkService)
      val mockMunicipalityDao: MunicipalityDao = MockitoSugar.mock[MunicipalityDao]

      val roadLink = Seq(RoadLink(11, List(Point(0.0,0.0), Point(10.0, 0.0)), 10, State, 1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235))),
      RoadLink(21, List(Point(0.0,0.0), Point(20.0, 0.0)), 20, State, 1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(91))))

      val ids = Seq(RollbackMassTransitStopService.create(NewMassTransitStop(5.0, 0.0, 1l, 2,
        Seq(
          SimplePointAssetProperty("tietojen_yllapitaja", Seq(PropertyValue("1"))),
          SimplePointAssetProperty("pysakin_tyyppi", Seq(PropertyValue("2"))),
          SimplePointAssetProperty("vaikutussuunta", Seq(PropertyValue("2"))),
          SimplePointAssetProperty("matkustajatunnus", Seq(PropertyValue("1000")))
        )), "masstransitstopservice_spec", roadLink.head),
      RollbackMassTransitStopService.create(NewMassTransitStop(15.0, 0.0, 1l, 2,
        Seq(
          SimplePointAssetProperty("tietojen_yllapitaja", Seq(PropertyValue("1"))),
          SimplePointAssetProperty("pysakin_tyyppi", Seq(PropertyValue("2"))),
          SimplePointAssetProperty("vaikutussuunta", Seq(PropertyValue("2"))),
          SimplePointAssetProperty("matkustajatunnus", Seq(PropertyValue("1000")))
        )), "masstransitstopservice_spec", roadLink.last))

      val result = service.getMassTransitStopByPassengerId("1000", _ => Unit)
      result should have size 2
      result.find(_.id == ids.head).head.municipalityName should be (Some("Kauniainen"))
      result.find(_.id == ids.last).last.municipalityName should be (Some("Helsinki"))
      }
    }
  }

