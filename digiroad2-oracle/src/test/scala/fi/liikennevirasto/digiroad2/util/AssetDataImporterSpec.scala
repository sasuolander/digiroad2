package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.asset.oracle.{Queries, Sequences}
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import org.scalatest.{Matchers, FunSuite}
import slick.driver.JdbcDriver.backend.Database
import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.StaticQuery
import slick.jdbc.StaticQuery.interpolation

class AssetDataImporterSpec extends FunSuite with Matchers {
  private val assetDataImporter = new AssetDataImporter {
    override def withDynTransaction(f: => Unit): Unit = f
    override def withDynSession[T](f: => T): T = f
  }

  test("Split multi-link speed limit assets") {
    TestTransactions.runWithRollback() {
      val originalId = createMultiLinkLinearAsset(20, Seq(LinearAssetSegment(1, 0, 50), LinearAssetSegment(2, 0, 50)))
      insertSpeedLimitValue(originalId, 60)

      assetDataImporter.splitMultiLinkSpeedLimitsToSingleLinkLimits()

      val splitSegments = fetchSpeedLimitSegments(s"split_speedlimit_$originalId")

      splitSegments.length shouldBe 2
      splitSegments(0)._1 shouldNot be(splitSegments(1)._1)
      splitSegments(0)._6 should be(60)
      splitSegments(1)._6 should be(60)
      splitSegments.map(_._3).toSet should be(Set(1, 2))
      splitSegments(0)._7 should be(false)
      splitSegments(1)._7 should be(false)

      val originalSpeedLimitSegments = fetchSpeedLimitSegments("asset_data_importer_spec")

      originalSpeedLimitSegments.length should be(2)
      originalSpeedLimitSegments(0)._7 should be(true)
      originalSpeedLimitSegments(1)._7 should be(true)
      originalSpeedLimitSegments(0)._1 should be(originalId)
      originalSpeedLimitSegments(1)._1 should be(originalId)
    }
  }

  test("Split multi-link total weight limit assets") {
    TestTransactions.runWithRollback() {
      val originalId = createMultiLinkLinearAsset(30, Seq(LinearAssetSegment(1, 0, 50), LinearAssetSegment(2, 0, 50)))
      insertNumericalLimitValue(originalId, 40000)

      assetDataImporter.splitMultiLinkAssetsToSingleLinkAssets(30)

      val splitSegments = fetchNumericalLimitSegments(s"split_linearasset_$originalId")

      splitSegments.length shouldBe 2
      splitSegments(0)._1 shouldNot be(splitSegments(1)._1)
      splitSegments(0)._6 should be(Some(40000))
      splitSegments(1)._6 should be(Some(40000))
      splitSegments.map(_._3).toSet should be(Set(1, 2))
      splitSegments(0)._7 should be(false)
      splitSegments(1)._7 should be(false)

      val originalSpeedLimitSegments = fetchNumericalLimitSegments("asset_data_importer_spec")

      originalSpeedLimitSegments.length should be(2)
      originalSpeedLimitSegments(0)._7 should be(true)
      originalSpeedLimitSegments(1)._7 should be(true)
      originalSpeedLimitSegments(0)._1 should be(originalId)
      originalSpeedLimitSegments(1)._1 should be(originalId)
    }
  }

  test("Split multi-link lit road assets") {
    TestTransactions.runWithRollback() {
      val originalId = createMultiLinkLinearAsset(100, Seq(LinearAssetSegment(1, 0, 50), LinearAssetSegment(2, 0, 50)))

      assetDataImporter.splitMultiLinkAssetsToSingleLinkAssets(100)

      val splitSegments = fetchNumericalLimitSegments(s"split_linearasset_$originalId")

      splitSegments.length shouldBe 2
      splitSegments(0)._1 shouldNot be(splitSegments(1)._1)
      splitSegments(0)._6 should be(None)
      splitSegments(1)._6 should be(None)
      splitSegments.map(_._3).toSet should be(Set(1, 2))
      splitSegments(0)._7 should be(false)
      splitSegments(1)._7 should be(false)

      val originalSpeedLimitSegments = fetchNumericalLimitSegments("asset_data_importer_spec")

      originalSpeedLimitSegments.length should be(2)
      originalSpeedLimitSegments(0)._7 should be(true)
      originalSpeedLimitSegments(1)._7 should be(true)
      originalSpeedLimitSegments(0)._1 should be(originalId)
      originalSpeedLimitSegments(1)._1 should be(originalId)
    }
  }

  case class LinearAssetSegment(mmlId: Long, startMeasure: Double, endMeasure: Double)

  private def createMultiLinkLinearAsset(typeId: Int, segments: Seq[LinearAssetSegment]): Long = {
    val speedLimitId = Sequences.nextPrimaryKeySeqValue

    sqlu"""
      insert
        into asset(id, asset_type_id, created_by, created_date)
        values ($speedLimitId, $typeId, 'asset_data_importer_spec', sysdate)
    """.execute

    segments.foreach { segment =>
      val lrmPositionId = Sequences.nextLrmPositionPrimaryKeySeqValue
      val startMeasure = segment.startMeasure
      val endMeasure = segment.endMeasure
      val mmlId = segment.mmlId
      sqlu"""
        insert all
          into lrm_position(id, start_measure, end_measure, mml_id, side_code)
          values ($lrmPositionId, $startMeasure, $endMeasure, $mmlId, 1)

          into asset_link(asset_id, position_id)
          values ($speedLimitId, $lrmPositionId)
        select * from dual
      """.execute
    }
    speedLimitId
  }

  private def insertSpeedLimitValue(assetId: Long, value: Int): Unit = {
    val propertyId = StaticQuery.query[String, Long](Queries.propertyIdByPublicId).apply("rajoitus").first

    sqlu"""
      insert into single_choice_value(asset_id, enumerated_value_id, property_id, modified_date)
      values ($assetId, (select id from enumerated_value where property_id = $propertyId and value = $value), $propertyId, current_timestamp)
      """.execute
  }

  private def insertNumericalLimitValue(assetId: Long, value: Int): Unit = {
    val numberPropertyValueId = Sequences.nextPrimaryKeySeqValue
    val propertyId = StaticQuery.query[String, Long](Queries.propertyIdByPublicId).apply("mittarajoitus").first

    sqlu"""
      insert into number_property_value(id, asset_id, property_id, value)
      values ($numberPropertyValueId, $assetId, $propertyId, $value)
      """.execute
  }

  private def fetchNumericalLimitSegments(creator: String): List[(Long, Long, Long, Double, Double, Option[Int], Boolean)] = {
    sql"""
        select a.id, lrm.id, lrm.mml_id, lrm.start_measure, lrm.end_measure, n.value, a.floating
        from asset a
        join asset_link al on al.asset_id = a.id
        join lrm_position lrm on lrm.id = al.position_id
        left join number_property_value n on a.id = n.asset_id
        where a.created_by = $creator
      """.as[(Long, Long, Long, Double, Double, Option[Int], Boolean)].list
  }

  private def fetchSpeedLimitSegments(creator: String): List[(Long, Long, Long, Double, Double, Int, Boolean)] = {
    sql"""
        select a.id, lrm.id, lrm.mml_id, lrm.start_measure, lrm.end_measure, e.value, a.floating
        from asset a
        join asset_link al on al.asset_id = a.id
        join lrm_position lrm on lrm.id = al.position_id
        join single_choice_value s on a.id = s.asset_id
        join enumerated_value e on e.id = s.enumerated_value_id
        where a.created_by = $creator
      """.as[(Long, Long, Long, Double, Double, Int, Boolean)].list
  }
}
