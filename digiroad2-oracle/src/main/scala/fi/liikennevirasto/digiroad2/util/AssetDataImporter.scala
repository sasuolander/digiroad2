package fi.liikennevirasto.digiroad2.util

import java.io.{BufferedWriter, File, FileWriter}
import java.util.Properties
import javax.sql.DataSource

import com.jolbox.bonecp.{BoneCPConfig, BoneCPDataSource}
import fi.liikennevirasto.digiroad2.asset.SideCode
import fi.liikennevirasto.digiroad2.linearasset._
import fi.liikennevirasto.digiroad2.linearasset.oracle.OracleLinearAssetDao
import fi.liikennevirasto.digiroad2.pointasset.oracle.PedestrianCrossing
import org.joda.time.format.{PeriodFormat, PeriodFormatterBuilder}
import slick.driver.JdbcDriver.backend.{Database, DatabaseDef}
import Database.dynamicSession
import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.masstransitstop.oracle.{Queries, Sequences}
import fi.liikennevirasto.digiroad2.oracle.{MassQuery, OracleDatabase}
import fi.liikennevirasto.digiroad2.ConversionDatabase._
import fi.liikennevirasto.digiroad2.util.AssetDataImporter.{SimpleBusStop, _}
import fi.liikennevirasto.digiroad2.masstransitstop.oracle.Queries.updateAssetGeometry
import _root_.oracle.sql.STRUCT
import org.joda.time._
import org.slf4j.LoggerFactory
import scala.collection.mutable
import scala.collection.parallel.ForkJoinTaskSupport
import scala.concurrent.forkjoin.ForkJoinPool
import slick.jdbc.StaticQuery.interpolation
import slick.jdbc._
import com.github.tototoshi.slick.MySQLJodaSupport._

import slick.util.CloseableIterator

object AssetDataImporter {
  case class SimpleBusStop(shelterType: Int,
                           assetId: Option[Long] = None,
                           busStopId: Option[Long],
                           busStopType: Seq[Int],
                           lrmPositionId: Long,
                           validFrom: LocalDate = LocalDate.now,
                           validTo: Option[LocalDate] = None,
                           point: Point,
                           roadLinkId: Long,
                           municipalityCode: Int,
                           bearing: Double)
  case class SimpleRoadLink(id: Long, roadType: Int, roadNumber: Int, roadPartNumber: Int, functionalClass: Int, rStartHn: Int, lStartHn: Int,
                            rEndHn: Int, lEndHn: Int, municipalityNumber: Int, geom: STRUCT)

  case class PropertyWrapper(shelterTypePropertyId: Long, accessibilityPropertyId: Long, administratorPropertyId: Long,
                             busStopAssetTypeId: Long, busStopTypePropertyId: Long)

  sealed trait ImportDataSet {
    def database(): DatabaseDef
  }

  case object TemporaryTables extends ImportDataSet {
    lazy val dataSource: DataSource = {
      val cfg = new BoneCPConfig(OracleDatabase.loadProperties("/import.bonecp.properties"))
      new BoneCPDataSource(cfg)
    }

    def database() = Database.forDataSource(dataSource)
  }

  case object Conversion extends ImportDataSet {
    lazy val dataSource: DataSource = {
      val cfg = new BoneCPConfig(OracleDatabase.loadProperties("/conversion.bonecp.properties"))
      new BoneCPDataSource(cfg)
    }

    def database() = Database.forDataSource(dataSource)
    val roadLinkTable: String = "tielinkki"
    val busStopTable: String = "lineaarilokaatio"
  }

  def humanReadableDurationSince(startTime: DateTime): String = {
    PeriodFormat.getDefault.print(new Period(startTime, DateTime.now()))
  }
}

class AssetDataImporter {
  val logger = LoggerFactory.getLogger(getClass)
  lazy val ds: DataSource = initDataSource

  val Modifier = "dr1conversion"

  def withDynTransaction(f: => Unit): Unit = OracleDatabase.withDynTransaction(f)
  def withDynSession[T](f: => T): T = OracleDatabase.withDynSession(f)

  implicit object SetStruct extends SetParameter[STRUCT] {
    def apply(v: STRUCT, pp: PositionedParameters) {
      pp.setObject(v, java.sql.Types.STRUCT)
    }
  }

  def time[A](f: => A) = {
    val s = System.nanoTime
    val ret = f
    println("time for insert "+(System.nanoTime-s)/1e6+"ms")
    ret
  }

  private def getBatchDrivers(size: Int): List[(Int, Int)] = {
    println(s"""creating batching for $size items""")
    getBatchDrivers(1, size, 500)
  }

  def getBatchDrivers(n: Int, m: Int, step: Int): List[(Int, Int)] = {
    if ((m - n) < step) {
      List((n, m))
    } else {
      val x = ((n to m by step).sliding(2).map(x => (x(0), x(1) - 1))).toList
      x :+ (x.last._2 + 1, m)
    }
  }

  def expireSplitAssetsWithoutMml(typeId: Int) = {
    val chunkSize = 1000
    val splitAssetsWithoutLinkIdFilter = """
      a.created_by like 'split_linearasset_%'
      and lrm.link_id is null
      and (a.valid_to > sysdate or a.valid_to is null)"""
    val (minId, maxId) = getAssetIdRangeWithFilter(typeId, splitAssetsWithoutLinkIdFilter)
    val chunks: List[(Int, Int)] = getBatchDrivers(minId, maxId, chunkSize)
    chunks.foreach { case(chunkStart, chunkEnd) =>
      val start = System.currentTimeMillis()
      expireSplitLinearAssetsWithoutLinkId(typeId, chunkStart, chunkEnd)
      println("*** Processed linear assets between " + chunkStart + " and " + chunkEnd + " in " + (System.currentTimeMillis() - start) + " ms.")
    }
  }

  def importEuropeanRoads(conversionDatabase: DatabaseDef, vvhHost: String) = {
    val roads = conversionDatabase.withDynSession {
      sql"""select link_id, eur_nro from eurooppatienumero""".as[(Long, String)].list
    }

    val roadsByLinkId = roads.foldLeft(Map.empty[Long, (Long, String)]) { (m, road) => m + (road._1 -> road) }

    val vvhClient = new VVHClient(vvhHost)
    val vvhLinks = vvhClient.fetchVVHRoadlinks(roadsByLinkId.keySet)
    val linksByLinkId = vvhLinks.foldLeft(Map.empty[Long, VVHRoadlink]) { (m, link) => m + (link.linkId -> link) }

    val roadsWithLinks = roads.map { road => (road, linksByLinkId.get(road._1)) }

    OracleDatabase.withDynTransaction {
      val assetPS = dynamicSession.prepareStatement("insert into asset (id, asset_type_id, floating, CREATED_DATE, CREATED_BY) values (?, ?, ?, SYSDATE, 'dr1_conversion')")
      val propertyPS = dynamicSession.prepareStatement("insert into text_property_value (id, asset_id, property_id, value_fi) values (?, ?, ?, ?)")
      val lrmPositionPS = dynamicSession.prepareStatement("insert into lrm_position (ID, link_id, SIDE_CODE, start_measure, end_measure) values (?, ?, ?, ?, ?)")
      val assetLinkPS = dynamicSession.prepareStatement("insert into asset_link (asset_id, position_id) values (?, ?)")

      val propertyId = Queries.getPropertyIdByPublicId("eurooppatienumero")

      roadsWithLinks.foreach { case ((linkId, eRoad), link) =>
        val assetId = Sequences.nextPrimaryKeySeqValue

        assetPS.setLong(1, assetId)
        assetPS.setInt(2, 260)
        assetPS.setBoolean(3, link.isEmpty)
        assetPS.addBatch()

        val lrmPositionId = Sequences.nextLrmPositionPrimaryKeySeqValue

        lrmPositionPS.setLong(1, lrmPositionId)
        lrmPositionPS.setLong(2, linkId)
        lrmPositionPS.setInt(3, SideCode.BothDirections.value)
        lrmPositionPS.setDouble(4, 0)
        lrmPositionPS.setDouble(5, link.map(_.geometry).map(GeometryUtils.geometryLength).getOrElse(0))
        lrmPositionPS.addBatch()

        assetLinkPS.setLong(1, assetId)
        assetLinkPS.setLong(2, lrmPositionId)
        assetLinkPS.addBatch()

        propertyPS.setLong(1, Sequences.nextPrimaryKeySeqValue)
        propertyPS.setLong(2, assetId)
        propertyPS.setLong(3, propertyId)
        propertyPS.setString(4, eRoad)
        propertyPS.addBatch()
      }

      assetPS.executeBatch()
      lrmPositionPS.executeBatch()
      assetLinkPS.executeBatch()
      propertyPS.executeBatch()

      assetPS.close()
      assetLinkPS.close()
      lrmPositionPS.close()
      propertyPS.close()
    }
  }

  def importProhibitions(conversionDatabase: DatabaseDef, vvhServiceHost: String) = {
    val conversionTypeId = 29
    val exceptionTypeId = 1
    val vvhClient = new VVHClient(vvhServiceHost)
    val typeId = 190

    println("*** Fetching prohibitions from conversion database")
    val startTime = DateTime.now()

    val prohibitions = conversionDatabase.withDynSession {
      sql"""
          select s.segm_id, t.link_id, s.alkum, s.loppum, t.kunta_nro, s.arvo, s.puoli, s.aika
          from segments s
          join tielinkki_ctas t on s.tielinkki_id = t.dr1_id
          where s.tyyppi = $conversionTypeId and s.kaista is null
       """.as[(Long, Long, Double, Double, Int, Int, Int, Option[String])].list
    }

    val exceptions = conversionDatabase.withDynSession {
      sql"""
          select s.segm_id, t.link_id, s.arvo, s.puoli
          from segments s
          join tielinkki_ctas t on s.tielinkki_id = t.dr1_id
          where s.tyyppi = $exceptionTypeId and s.kaista is null
       """.as[(Long, Long, Int, Int)].list
    }

    println(s"*** Fetched ${prohibitions.length} prohibitions from conversion database in ${humanReadableDurationSince(startTime)}")

    val roadLinks = vvhClient.fetchVVHRoadlinks(prohibitions.map(_._2).toSet)

    val conversionResults = convertToProhibitions(prohibitions, roadLinks, exceptions)
    println(s"*** Importing ${prohibitions.length} prohibitions")

    val insertCount = OracleDatabase.withDynTransaction {
      insertProhibitions(typeId, conversionResults)
    }
    println(s"*** Persisted $insertCount linear assets in ${humanReadableDurationSince(startTime)}")
  }

  def insertProhibitions(typeId: Int, conversionResults: Seq[Either[String, PersistedLinearAsset]]): Int = {
      val assetPS = dynamicSession.prepareStatement("insert into asset (id, asset_type_id, CREATED_DATE, CREATED_BY) values (?, ?, SYSDATE, 'dr1_conversion')")
      val lrmPositionPS = dynamicSession.prepareStatement("insert into lrm_position (ID, link_id, START_MEASURE, END_MEASURE, SIDE_CODE) values (?, ?, ?, ?, ?)")
      val assetLinkPS = dynamicSession.prepareStatement("insert into asset_link (asset_id, position_id) values (?, ?)")
      val valuePS = dynamicSession.prepareStatement("insert into prohibition_value (id, asset_id, type) values (?, ?, ?)")
      val exceptionPS = dynamicSession.prepareStatement("insert into prohibition_exception (id, prohibition_value_id, type) values (?, ?, ?)")
      val validityPeriodPS = dynamicSession.prepareStatement("insert into prohibition_validity_period (id, prohibition_value_id, type, start_hour, end_hour) values (?, ?, ?, ?, ?)")

      conversionResults.foreach {
        case Right(asset) =>
          val assetId = Sequences.nextPrimaryKeySeqValue
          assetPS.setLong(1, assetId)
          assetPS.setInt(2, typeId)
          assetPS.addBatch()

          val lrmPositionId = Sequences.nextLrmPositionPrimaryKeySeqValue
          lrmPositionPS.setLong(1, lrmPositionId)
          lrmPositionPS.setLong(2, asset.linkId)
          lrmPositionPS.setDouble(3, asset.startMeasure)
          lrmPositionPS.setDouble(4, asset.endMeasure)
          lrmPositionPS.setInt(5, asset.sideCode)
          lrmPositionPS.addBatch()

          assetLinkPS.setLong(1, assetId)
          assetLinkPS.setLong(2, lrmPositionId)
          assetLinkPS.addBatch()

          val value: Prohibitions = asset.value.get.asInstanceOf[Prohibitions]
          value.prohibitions.foreach { prohibitionValue =>
            val valueId = Sequences.nextPrimaryKeySeqValue
            valuePS.setLong(1, valueId)
            valuePS.setLong(2, assetId)
            valuePS.setLong(3, prohibitionValue.typeId)
            valuePS.addBatch()
            prohibitionValue.exceptions.foreach { exceptionType =>
              val exceptionId = Sequences.nextPrimaryKeySeqValue
              exceptionPS.setLong(1, exceptionId)
              exceptionPS.setLong(2, valueId)
              exceptionPS.setInt(3, exceptionType)
              exceptionPS.addBatch()
            }
            prohibitionValue.validityPeriods.foreach { validityPeriod =>
              val validityPeriodId = Sequences.nextPrimaryKeySeqValue
              validityPeriodPS.setLong(1, validityPeriodId)
              validityPeriodPS.setLong(2, valueId)
              validityPeriodPS.setInt(3, validityPeriod.days.value)
              validityPeriodPS.setInt(4, validityPeriod.startHour)
              validityPeriodPS.setInt(5, validityPeriod.endHour)
              validityPeriodPS.addBatch()
            }
          }
        case Left(validationError) => println(s"*** $validationError")
      }

      val executedAssetInserts = assetPS.executeBatch().length
      lrmPositionPS.executeBatch()
      assetLinkPS.executeBatch()
      valuePS.executeBatch()
      exceptionPS.executeBatch()
      validityPeriodPS.executeBatch()


      assetPS.close()
      lrmPositionPS.close()
      assetLinkPS.close()
      valuePS.close()
      exceptionPS.close()
      validityPeriodPS.close()
      executedAssetInserts
  }

  private def expandSegments(segments: Seq[(Long, Long, Double, Double, Int, Int, Int, Option[String])], exceptionSideCodes: Seq[Int]): Seq[(Long, Long, Double, Double, Int, Int, Int, Option[String])] = {
    if (segments.forall(_._7 == 1) && exceptionSideCodes.forall(_ == 1)) segments
    else {
      val (bothSided, oneSided) = segments.partition(_._7 == 1)
      val splitSegments = bothSided.flatMap { x => Seq(x.copy(_7 = 2), x.copy(_7 = 3)) }
      splitSegments ++ oneSided
    }
  }

  def expandExceptions(exceptions: Seq[(Long, Long, Int, Int)], prohibitionSideCodes: Seq[(Int)]) = {
    if (exceptions.forall(_._4 == 1) && prohibitionSideCodes.forall(_ == 1)) exceptions
    else {
      val (bothSided, oneSided) = exceptions.partition(_._4 == 1)
      val splitExceptions = bothSided.flatMap { x => Seq(x.copy(_4 = 2), x.copy(_4 = 3)) }
      splitExceptions ++ oneSided
    }
  }

  private def parseProhibitionValues(segments: Seq[(Long, Long, Double, Double, Int, Int, Int, Option[String])], exceptions: Seq[(Long, Long, Int, Int)], linkId: Long, sideCode: Int): Seq[Either[String, ProhibitionValue]] = {
    val timeDomainParser = new TimeDomainParser
    segments.map { segment =>
      val exceptionsForProhibition = exceptions.filter { z => z._2 == linkId && z._4 == sideCode }.map(_._3).toSet

      segment._8 match {
        case None => Right(ProhibitionValue(segment._6, Set.empty, exceptionsForProhibition))
        case Some(timeDomainString) =>
          timeDomainParser.parse(timeDomainString) match {
            case Left(err) => Left(s"${err}. Dropped prohibition ${segment._1}.")
            case Right(periods) => Right(ProhibitionValue(segment._6, periods.toSet, exceptionsForProhibition))
          }
      }
    }
  }

  def convertToProhibitions(prohibitionSegments: Seq[(Long, Long, Double, Double, Int, Int, Int, Option[String])], roadLinks: Seq[VVHRoadlink], exceptions: Seq[(Long, Long, Int, Int)]): Seq[Either[String, PersistedLinearAsset]] = {
    def hasInvalidExceptionType(exception: (Long, Long, Int, Int)): Boolean = {
      !Set(21, 22, 10, 9, 27, 5, 8, 7, 6, 4, 15, 19, 13, 14, 24, 25).contains(exception._3)
    }

    def hasInvalidProhibitionType(prohibition: (Long, Long, Double, Double, Int, Int, Int, Option[String])): Boolean = {
      !Set(3, 2, 23, 12, 11, 26, 10, 9, 27, 5, 8, 7, 6, 4, 15, 19, 13, 14, 24, 25).contains(prohibition._6)
    }
    val (segmentsWithRoadLink, segmentsWithoutRoadLink) = prohibitionSegments.partition { s => roadLinks.exists(_.linkId == s._2) }
    val (segmentsOfInvalidType, validSegments) = segmentsWithRoadLink.partition { s => hasInvalidProhibitionType(s) }
    val segmentsByLinkId = validSegments.groupBy(_._2)
    val (exceptionsWithProhibition, exceptionsWithoutProhibition) = exceptions.partition { x => segmentsByLinkId.keySet.contains(x._2) }
    val (exceptionWithInvalidCode, validExceptions) = exceptionsWithProhibition.partition { x => hasInvalidExceptionType(x) }

    segmentsByLinkId.flatMap { case (linkId, segments) =>
      val roadLinkLength = GeometryUtils.geometryLength(roadLinks.find(_.linkId == linkId).get.geometry)
      val expandedSegments = expandSegments(segments, validExceptions.filter(_._2 == linkId).map(_._4))
      val expandedExceptions = expandExceptions(validExceptions.filter(_._2 == linkId), segments.map(_._7))

      expandedSegments.groupBy(_._7).flatMap { case (sideCode, segmentsPerSide) =>
        val prohibitionResults = parseProhibitionValues(segmentsPerSide, expandedExceptions, linkId, sideCode)
        val linearAssets = prohibitionResults.filter(_.isRight).map(_.right.get) match {
          case Nil => Nil
          case prohibitionValues => Seq(Right(PersistedLinearAsset(0l, linkId, sideCode, Some(Prohibitions(prohibitionValues)), 0.0, roadLinkLength, None, None, None, None, false, 190, 0, None)))
        }
        val parseErrors = prohibitionResults.filter(_.isLeft).map(_.left.get).map(Left(_))
        linearAssets ++ parseErrors
      }
    }.toSeq ++
      segmentsWithoutRoadLink.map { s => Left(s"No VVH road link found for mml id ${s._2}. ${s._1} dropped.") } ++
      segmentsOfInvalidType.map { s => Left(s"Invalid type for prohibition. ${s._1} dropped.") } ++
      exceptionsWithoutProhibition.map { ex => Left(s"No prohibition found on mml id ${ex._2}. Dropped exception ${ex._1}.")} ++
      exceptionWithInvalidCode.map { ex => Left(s"Invalid exception. Dropped exception ${ex._1}.")}
  }

  def fetchProhibitionsByLinkIds(prohibitionAssetTypeId: Int, ids: Seq[Long], includeFloating: Boolean = false): Seq[PersistedLinearAsset] = {
    val floatingFilter = if (includeFloating) "" else "and a.floating = 0"

    val assets = MassQuery.withIds(ids.toSet) { idTableName =>
      sql"""
        select a.id, pos.link_id, pos.side_code,
               pv.id, pv.type,
               pvp.type, pvp.start_hour, pvp.end_hour,
               pe.type,
               pos.start_measure, pos.end_measure,
               a.created_by, a.created_date, a.modified_by, a.modified_date,
               case when a.valid_to <= sysdate then 1 else 0 end as expired
          from asset a
          join asset_link al on a.id = al.asset_id
          join lrm_position pos on al.position_id = pos.id
          join prohibition_value pv on pv.asset_id = a.id
          join #$idTableName i on i.id = a.id
          left join prohibition_validity_period pvp on pvp.prohibition_value_id = pv.id
          left join prohibition_exception pe on pe.prohibition_value_id = pv.id
          where a.asset_type_id = $prohibitionAssetTypeId
          and (a.valid_to >= sysdate or a.valid_to is null)
          #$floatingFilter"""
        .as[(Long, Long, Int, Long, Int, Option[Int], Option[Int], Option[Int], Option[Int], Double, Double, Option[String], Option[DateTime], Option[String], Option[DateTime], Boolean)].list
    }

    val groupedByAssetId = assets.groupBy(_._1)
    val groupedByProhibitionId = groupedByAssetId.mapValues(_.groupBy(_._4))

    groupedByProhibitionId.map { case (assetId, rowsByProhibitionId) =>
      val (_, linkId, sideCode, _, _, _, _, _, _, startMeasure, endMeasure, createdBy, createdDate, modifiedBy, modifiedDate, expired) = groupedByAssetId(assetId).head
      val prohibitionValues = rowsByProhibitionId.keys.toSeq.sorted.map { prohibitionId =>
        val rows = rowsByProhibitionId(prohibitionId)
        val prohibitionType = rows.head._5
        val exceptions = rows.flatMap(_._9).toSet
        val validityPeriods = rows.filter(_._6.isDefined).map { case row =>
          ValidityPeriod(row._7.get, row._8.get, ValidityPeriodDayOfWeek(row._6.get))
        }.toSet
        ProhibitionValue(prohibitionType, validityPeriods, exceptions)
      }
      // TODO: when linear assets get included in change history
      PersistedLinearAsset(assetId, linkId, sideCode, Some(Prohibitions(prohibitionValues)), startMeasure, endMeasure, createdBy, createdDate, modifiedBy, modifiedDate, expired, prohibitionAssetTypeId, 0, None)
    }.toSeq
  }

  def importHazmatProhibitions() = {
    OracleDatabase.withDynTransaction {
      val assetIds =
        sql"""
          select a.id from asset a
             join prohibition_value pv on a.id = pv.ASSET_ID
             where pv.type in (24, 25)
             and a.asset_type_id = 190
        """.as[Long].list

      if (assetIds.nonEmpty) {
        val linearAssets = fetchProhibitionsByLinkIds(190, assetIds, true)
        val hazmatAssets = linearAssets.map { linearAsset =>
          val newValue = linearAsset.value.map {
            case Prohibitions(prohibitionValues) =>
              val hazMatProhibitions = prohibitionValues.filter { p => Set(24, 25).contains(p.typeId) }
              Prohibitions(hazMatProhibitions.map(_.copy(exceptions = Set.empty)))
            case x =>
              throw new IllegalArgumentException
          }
          linearAsset.copy(value = newValue)
        }

        insertProhibitions(210, hazmatAssets.map(Right(_)))

        val prohibitionValueIds = MassQuery.withIds(assetIds.toSet) { idTableName =>
           sql"""
              select pv.id from prohibition_value pv
              join #$idTableName i on i.id = pv.asset_id
              where type in (24, 25)""".as[Long].list
        }

        MassQuery.withIds(prohibitionValueIds.toSet) { idTableName =>
          sqlu"""delete from prohibition_validity_period
                 where prohibition_value_id in (select id from #$idTableName)""".execute
          sqlu"""delete from prohibition_exception where prohibition_value_id in (select id from #$idTableName)""".execute
        }

        MassQuery.withIds(assetIds.toSet) { idTableName =>
          sqlu"""delete from prohibition_value where asset_id in (select id from #$idTableName) and type in (24, 25)""".execute
        }

        sqlu"""delete from asset_link where asset_id in (select id from asset where asset_type_id=190 and id not in (select asset_id from prohibition_value))""".execute
        sqlu"""delete from asset where asset_type_id=190 and id not in (select asset_id from prohibition_value)""".execute
        sqlu"""delete from lrm_position pos where NOT EXISTS (select position_id from asset_link where position_id = pos.id)""".execute
      } else {
        println("No hazardous material prohibitions with old type id found for import")
      }
    }
  }

  def getTypeProperties = {
    OracleDatabase.withDynSession {
      val shelterTypePropertyId = sql"select p.id from property p where p.public_id = 'katos'".as[Long].first
      val accessibilityPropertyId = sql"select p.id from property p where p.public_id = 'esteettomyys_liikuntarajoitteiselle'".as[Long].first
      val administratorPropertyId = sql"select p.id from property p where p.public_id = 'tietojen_yllapitaja'".as[Long].first
      val busStopTypePropertyId = sql"select p.id from property p where p.public_id = 'pysakin_tyyppi'".as[Long].first
      val busStopAssetTypeId = sql"select id from asset_type where name = 'Bussipysäkit'".as[Long].first
      PropertyWrapper(shelterTypePropertyId, accessibilityPropertyId, administratorPropertyId,
                      busStopAssetTypeId, busStopTypePropertyId)
    }
  }

  def insertBusStops(busStop: SimpleBusStop, typeProps: PropertyWrapper) {
    OracleDatabase.withDynSession {
      val assetId = busStop.assetId.getOrElse(Sequences.nextPrimaryKeySeqValue)

      sqlu"""
        insert into asset(id, external_id, asset_type_id, created_by, valid_from, valid_to, municipality_code, bearing)
        values($assetId, ${busStop.busStopId}, ${typeProps.busStopAssetTypeId},
               $Modifier, ${busStop.validFrom}, ${busStop.validTo.getOrElse(null)},
               ${busStop.municipalityCode}, ${busStop.bearing})
      """.execute

      sqlu"""
         insert into asset_link(asset_id, position_id)
         values($assetId, ${busStop.lrmPositionId})
      """.execute

      updateAssetGeometry(assetId, busStop.point)
      busStop.busStopType.foreach { busStopType =>
        insertMultipleChoiceValue(typeProps.busStopTypePropertyId, assetId, busStopType)
      }

      insertTextPropertyData(typeProps.accessibilityPropertyId, assetId, "Ei tiedossa")
      insertSingleChoiceValue(typeProps.administratorPropertyId, assetId, 2)
      insertSingleChoiceValue(typeProps.shelterTypePropertyId, assetId, busStop.shelterType)
    }
  }

  def adjustToNewDigitization(vvhHost: String) = {
    val vvhClient = new VVHClient(vvhHost)
    val municipalities = OracleDatabase.withDynSession { Queries.getMunicipalities }
    val processedLinkIds = mutable.Set[Long]()

    withDynTransaction {
      municipalities.foreach { municipalityCode =>
        val startTime = DateTime.now()

        println(s"*** Fetching from VVH with municipality: $municipalityCode")

        val flippedLinks = vvhClient.fetchByMunicipality(municipalityCode)
          .filter(isHereFlipped)
          .filterNot(link => processedLinkIds.contains(link.linkId))

        var updatedCount = MassQuery.withIds(flippedLinks.map(_.linkId).toSet) { idTableName =>
          sqlu"""
            update lrm_position pos
            set pos.side_code = 5 - pos.side_code
            where exists(select * from #$idTableName i where i.id = pos.link_id)
            and pos.side_code in (2, 3)
          """.first +
          sqlu"""
            update traffic_direction td
            set td.traffic_direction = 7 - td.traffic_direction
            where exists(select id from #$idTableName i where i.id = td.link_id)
            and td.traffic_direction in (3, 4)
          """.first +
          sqlu"""
            update asset a
            set a.bearing = mod((a.bearing + 180), 360)
            where a.id in (select al.asset_id from asset_link al
                           join lrm_position pos on al.position_id = pos.id
                           join #$idTableName i on i.id = pos.link_id)
            and a.bearing is not null
            and a.asset_type_id in (10, 240)
          """.first
        }

        flippedLinks.foreach { link =>
          val length = GeometryUtils.geometryLength(link.geometry)

          updatedCount += sqlu"""
              update lrm_position
              set end_measure = greatest(0, ${length} - COALESCE(start_measure, 0)),
                  start_measure = greatest(0, ${length} - COALESCE(end_measure, start_measure, 0))
              where link_id = ${link.linkId}
            """.first
        }

        processedLinkIds ++= flippedLinks.map(_.linkId)

        println(s"*** Made $updatedCount updates in ${humanReadableDurationSince(startTime)}")
      }
    }
  }

  private def isHereFlipped(roadLink: VVHRoadlink) = {
    val NotFlipped = 0
    val Flipped = 1
    roadLink.attributes.getOrElse("MTKHEREFLIP", NotFlipped).asInstanceOf[BigInt] == Flipped
  }

  def generateValuesForLitRoads(): Unit = {
    processInChunks(100, "lit roads") { (chunkStart, chunkEnd) =>
      withDynTransaction {
        val litRoads = sql"""
          select a.id, p.id
            from asset a
            join asset_link al on a.id = al.asset_id
            join lrm_position pos on al.position_id = pos.id
            join property p on p.public_id = 'mittarajoitus'
            left join number_property_value s on s.asset_id = a.id and s.property_id = p.id
            where a.id between $chunkStart and $chunkEnd and a.asset_type_id = 100
        """.as[(Long, Long)].list

        litRoads.foreach { case (assetId, propertyId) =>
          val id = Sequences.nextPrimaryKeySeqValue
          sqlu"""
            insert into number_property_value (id, asset_id, property_id, value)
            values ($id, $assetId, $propertyId, 1)
          """.execute
        }

        println(s"Assigned value to ${litRoads.length} lit road assets")
      }
    }
  }

  def processInChunks(typeId: Int, assetTypeName: String)(f: (Int, Int) => Unit): Unit = {
    val chunkSize = 1000
    val (minId, maxId) = getAssetIdRange(typeId, true)
    val chunks: List[(Int, Int)] = getBatchDrivers(minId, maxId, chunkSize)
    chunks.foreach { case (chunkStart, chunkEnd) =>
      val startTime = System.currentTimeMillis()
      f(chunkStart, chunkEnd)
      println(s"*** Processed $assetTypeName between $chunkStart and $chunkEnd in ${System.currentTimeMillis() - startTime} ms.")
    }
  }
  private def getAssetIdRange(typeId: Int, includeSingleLinkAssets: Boolean = false): (Int, Int) = {
    val multiSegmentFilter = if (includeSingleLinkAssets) "" else "and (select count(*) from asset_link where asset_id = a.id) > 1"
    withDynSession {
      sql"""
        select min(a.id), max(a.id)
        from asset a
        where a.asset_type_id = $typeId and floating = 0 #$multiSegmentFilter
      """.as[(Int, Int)].first
    }
  }

  private def getAssetIdRangeWithFilter(typeId: Int, filter: String): (Int, Int) = {
    withDynSession {
      sql"""
        select min(a.id), max(a.id)
        from asset a
        join asset_link al on al.asset_id = a.id
        join lrm_position lrm on lrm.id = al.position_id
        where a.asset_type_id = $typeId and #$filter
      """.as[(Int, Int)].first
    }
  }
  private def getAssetIdRangeOfMultiLinkAssets(typeId: Int): (Int, Int) = {
    withDynSession {
      sql"""
        select min(a.id), max(a.id)
        from asset a
        where a.asset_type_id = $typeId and floating = 0 and (select count(*) from asset_link where asset_id = a.id) > 1
      """.as[(Int, Int)].first
    }
  }
  
  private def splitSpeedLimits(chunkStart: Long, chunkEnd: Long) = {
    val dao = new OracleLinearAssetDao(null)

    withDynTransaction {
      val speedLimitLinks = sql"""
            select a.id, pos.link_id, pos.side_code, e.value, pos.start_measure, pos.end_measure
            from asset a
            join asset_link al on a.id = al.asset_id
            join lrm_position pos on al.position_id = pos.id
            join property p on a.asset_type_id = p.asset_type_id and p.public_id = 'rajoitus'
            join single_choice_value s on s.asset_id = a.id and s.property_id = p.id
            join enumerated_value e on s.enumerated_value_id = e.id
            where a.asset_type_id = 20
            and floating = 0
            and (select count(*) from asset_link where asset_id = a.id) > 1
            and a.id between $chunkStart and $chunkEnd
          """.as[(Long, Long, Int, Option[Int], Double, Double)].list

      speedLimitLinks.foreach { speedLimitLink =>
        val (id, linkId, sideCode, value, startMeasure, endMeasure) = speedLimitLink
        dao.forceCreateLinearAsset(s"split_speedlimit_$id", 20, linkId, (startMeasure, endMeasure), SideCode(sideCode), value, (id, value) => dao.insertEnumeratedValue(id, "rajoitus", value))
      }
      println(s"created ${speedLimitLinks.length} new single link speed limits")

      val speedLimitsToFloat = speedLimitLinks.map(_._1).toSet
      dao.floatLinearAssets(speedLimitsToFloat)
      println(s"removed ${speedLimitsToFloat.size} multilink speed limits")
    }
  }

  private def splitLinearAssets(typeId: Int, chunkStart: Long, chunkEnd: Long) = {
    val dao = new OracleLinearAssetDao(null)

    withDynTransaction {
      val linearAssetLinks = sql"""
            select a.id, pos.link_id, pos.side_code, pos.start_measure, pos.end_measure, n.value
            from asset a
            join asset_link al on a.id = al.asset_id
            join lrm_position pos on al.position_id = pos.id
            left join number_property_value n on a.id = n.asset_id
            where a.asset_type_id = $typeId
            and floating = 0
            and (select count(*) from asset_link where asset_id = a.id) > 1
            and a.id between $chunkStart and $chunkEnd
          """.as[(Long, Long, Int, Double, Double, Option[Int])].list

      linearAssetLinks.foreach { case (id, linkId, sideCode, startMeasure, endMeasure, value) =>
        dao.forceCreateLinearAsset(s"split_linearasset_$id", typeId, linkId, (startMeasure, endMeasure), SideCode(sideCode), value, (id, value) => dao.insertValue(id, "mittarajoitus", value))
      }

      println(s"created ${linearAssetLinks.length} new single link linear assets")

      val assetsIdsToExpire = linearAssetLinks.map(_._1).toSet
      if (assetsIdsToExpire.size > 0) {
        val assetsIdsToExpireString = assetsIdsToExpire.mkString(",")
        sqlu"""update asset
               set modified_by = 'expired_splitted_linearasset', modified_date = sysdate, valid_to = sysdate
               where id in (#$assetsIdsToExpireString)""".execute
      }
      println(s"removed ${assetsIdsToExpire.size} multilink assets")
    }
  }

  private def expireSplitLinearAssetsWithoutLinkId(typeId: Int, chunkStart: Long, chunkEnd: Long) = {
    withDynTransaction {
      sqlu"""
        update asset
          set modified_by = 'expired_asset_without_mml', modified_date = sysdate, valid_to = sysdate
          where id in (
            select a.id
            from asset a
            join asset_link al on al.asset_id = a.id
            join lrm_position lrm on lrm.id = al.position_id
            where a.created_by like 'split_linearasset_%'
            and lrm.link_id is null
            and (a.valid_to > sysdate or a.valid_to is null)
            and a.id between $chunkStart and $chunkEnd
            and a.asset_type_id = $typeId)
        """.execute

      println(s"expired assets with ids between $chunkStart and $chunkEnd")
    }
  }

  def splitMultiLinkSpeedLimitsToSingleLinkLimits() {
    val chunkSize = 1000
    val (minId, maxId) = getAssetIdRange(20)
    val chunks: List[(Int, Int)] = getBatchDrivers(minId, maxId, chunkSize)
    chunks.foreach { case(chunkStart, chunkEnd) =>
      val start = System.currentTimeMillis()
      splitSpeedLimits(chunkStart, chunkEnd)
      println("*** Processed speed limits between " + chunkStart + " and " + chunkEnd + " in " + (System.currentTimeMillis() - start) + " ms.")
    }
  }

  def splitMultiLinkAssetsToSingleLinkAssets(typeId: Int) {
    val chunkSize = 1000
    val multiLinkLinearAssetsFilter = """
        (a.valid_to > sysdate or a.valid_to is null)
        and (select count(*) from asset_link where asset_id = a.id) > 1"""
    val (minId, maxId) = getAssetIdRangeWithFilter(typeId, multiLinkLinearAssetsFilter)
    val chunks: List[(Int, Int)] = getBatchDrivers(minId, maxId, chunkSize)
    chunks.foreach { case(chunkStart, chunkEnd) =>
      val start = System.currentTimeMillis()
      splitLinearAssets(typeId, chunkStart, chunkEnd)
      println("*** Processed linear assets between " + chunkStart + " and " + chunkEnd + " in " + (System.currentTimeMillis() - start) + " ms.")
    }
  }

  def unfloatLinearAssets(): Unit = {
    withDynTransaction {
      sqlu"""
        update asset a set floating=0
        where a.asset_type_id in (30,40,50,60,70,80,90,100)
        and (select count(*) from asset_link where asset_id = a.id) > 1""".execute
    }
  }

  def insertTextPropertyData(propertyId: Long, assetId: Long, text:String) {
    sqlu"""
      insert into text_property_value(id, property_id, asset_id, value_fi, value_sv, created_by)
      values (primary_key_seq.nextval, $propertyId, $assetId, $text, ' ', $Modifier)
    """.execute
  }

  def insertMultipleChoiceValue(propertyId: Long, assetId: Long, value: Int) {
    sqlu"""
      insert into multiple_choice_value(id, property_id, asset_id, enumerated_value_id, modified_by)
      values (primary_key_seq.nextval, $propertyId, $assetId,
        (select id from enumerated_value where value = $value and property_id = $propertyId), $Modifier)
    """.execute
  }

  def insertSingleChoiceValue(propertyId: Long, assetId: Long, value: Int) {
    sqlu"""
      insert into single_choice_value(property_id, asset_id, enumerated_value_id, modified_by)
      values ($propertyId, $assetId, (select id from enumerated_value where value = $value and property_id = $propertyId), $Modifier)
    """.execute
  }

  private[this] def initDataSource: DataSource = {
    Class.forName("oracle.jdbc.driver.OracleDriver")
    val cfg = new BoneCPConfig(localProperties)
    new BoneCPDataSource(cfg)
  }

  lazy val localProperties: Properties = {
    val props = new Properties()
    try {
      props.load(getClass.getResourceAsStream("/bonecp.properties"))
    } catch {
      case e: Exception => throw new RuntimeException("Can't load local.properties for env: " + System.getProperty("env"), e)
    }
    props
  }
}
