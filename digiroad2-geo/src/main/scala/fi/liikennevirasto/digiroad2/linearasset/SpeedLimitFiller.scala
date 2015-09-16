package fi.liikennevirasto.digiroad2.linearasset

import fi.liikennevirasto.digiroad2.GeometryUtils
import fi.liikennevirasto.digiroad2.asset.{AdministrativeClass, SideCode}

object SpeedLimitFiller {
  case class AdjustedSpeedLimitSegment(speedLimitSegment: SpeedLimit, adjustedMValue: Option[Double])
  case class MValueAdjustment(assetId: Long, mmlId: Long, startMeasure: Double, endMeasure: Double)
  case class SideCodeAdjustment(assetId: Long, sideCode: SideCode)
  case class UnknownLimit(mmlId: Long, municipalityCode: Int, administrativeClass: AdministrativeClass)
  case class SpeedLimitChangeSet(droppedSpeedLimitIds: Set[Long],
                                 adjustedMValues: Seq[MValueAdjustment],
                                 adjustedSideCodes: Seq[SideCodeAdjustment],
                                 generatedUnknownLimits: Seq[UnknownLimit])

  private val MaxAllowedMValueError = 0.5

  private def adjustSegment(link: SpeedLimit, roadLink: RoadLinkForSpeedLimit): (SpeedLimit, Seq[MValueAdjustment]) = {
    val startError = link.startMeasure
    val roadLinkLength = GeometryUtils.geometryLength(roadLink.geometry)
    val endError = roadLinkLength - link.endMeasure
    val mAdjustment =
      if (startError > MaxAllowedMValueError || endError > MaxAllowedMValueError)
        Seq(MValueAdjustment(link.id, link.mmlId, 0, roadLinkLength))
      else
        Nil
    val modifiedSegment = link.copy(geometry = GeometryUtils.truncateGeometry(roadLink.geometry, 0, roadLinkLength), startMeasure = 0, endMeasure = roadLinkLength)
    (modifiedSegment, mAdjustment)
  }

  private def adjustTwoWaySegments(roadLink: RoadLinkForSpeedLimit,
                                   segments: Seq[SpeedLimit]):
  (Seq[SpeedLimit], Seq[MValueAdjustment]) = {
    val twoWaySegments = segments.filter(_.sideCode == SideCode.BothDirections)
    if (twoWaySegments.length == 1 && segments.forall(_.sideCode == SideCode.BothDirections)) {
      val segment = segments.head
      val (adjustedSegment, mValueAdjustments) = adjustSegment(segment, roadLink)
      (Seq(adjustedSegment), mValueAdjustments)
    } else {
      (twoWaySegments, Nil)
    }
  }

  private def adjustOneWaySegments(roadLink: RoadLinkForSpeedLimit,
                                   segments: Seq[SpeedLimit],
                                   runningDirection: SideCode):
  (Seq[SpeedLimit], Seq[MValueAdjustment]) = {
    val segmentsTowardsRunningDirection = segments.filter(_.sideCode == runningDirection)
    if (segmentsTowardsRunningDirection.length == 1 && segments.filter(_.sideCode == SideCode.BothDirections).isEmpty) {
      val segment = segmentsTowardsRunningDirection.head
      val (adjustedSegment, mValueAdjustments) = adjustSegment(segment, roadLink)
      (Seq(adjustedSegment), mValueAdjustments)
    } else {
      (segmentsTowardsRunningDirection, Nil)
    }
  }

  private def adjustSegmentMValues(roadLink: RoadLinkForSpeedLimit, segments: Seq[SpeedLimit], changeSet: SpeedLimitChangeSet): (Seq[SpeedLimit], SpeedLimitChangeSet) = {
    val (towardsGeometrySegments, towardsGeometryAdjustments) = adjustOneWaySegments(roadLink, segments, SideCode.TowardsDigitizing)
    val (againstGeometrySegments, againstGeometryAdjustments) = adjustOneWaySegments(roadLink, segments, SideCode.AgainstDigitizing)
    val (twoWayGeometrySegments, twoWayGeometryAdjustments) = adjustTwoWaySegments(roadLink, segments)
    val mValueAdjustments = towardsGeometryAdjustments ++ againstGeometryAdjustments ++ twoWayGeometryAdjustments
    (towardsGeometrySegments ++ againstGeometrySegments ++ twoWayGeometrySegments,
      changeSet.copy(adjustedMValues = changeSet.adjustedMValues ++ mValueAdjustments))
  }

  private def adjustSegmentSideCodes(roadLink: RoadLinkForSpeedLimit, segments: Seq[SpeedLimit], changeSet: SpeedLimitChangeSet): (Seq[SpeedLimit], SpeedLimitChangeSet) = {
    if (segments.length == 1 && segments.head.sideCode != SideCode.BothDirections) {
      val segment = segments.head
      val sideCodeAdjustments = Seq(SideCodeAdjustment(segment.id, SideCode.BothDirections))
      (Seq(segment.copy(sideCode = SideCode.BothDirections)), changeSet.copy(adjustedSideCodes = changeSet.adjustedSideCodes ++ sideCodeAdjustments))
    } else {
      (segments, changeSet)
    }
  }

  private def dropRedundantSegments(roadLink: RoadLinkForSpeedLimit, segments: Seq[SpeedLimit], changeSet: SpeedLimitChangeSet): (Seq[SpeedLimit], SpeedLimitChangeSet) = {
    val headOption = segments.headOption
    val valueShared = segments.length > 1 && headOption.exists(first => segments.forall(_.value == first.value))
    valueShared match {
      case true =>
        val first = headOption.get
        val rest = segments.tail
        val segmentDrops = rest.map(_.id).toSet
        (Seq(first), changeSet.copy(droppedSpeedLimitIds = changeSet.droppedSpeedLimitIds ++ segmentDrops))
      case false => (segments, changeSet)
    }
  }

  private def dropSpeedLimitsWithEmptySegments(speedLimits: Map[Long, Seq[SpeedLimit]]): Set[Long] = {
    speedLimits.filter { case (id, segments) => segments.exists(_.geometry.isEmpty) }.keySet
  }

  private def dropShortLimits(roadLinks: RoadLinkForSpeedLimit, speedLimits: Seq[SpeedLimit], changeSet: SpeedLimitChangeSet): (Seq[SpeedLimit], SpeedLimitChangeSet) = {
    val limitsToDrop = speedLimits.filter { limit => GeometryUtils.geometryLength(limit.geometry) < MaxAllowedMValueError }.map(_.id).toSet
    val limits = speedLimits.filterNot { x => limitsToDrop.contains(x.id) }
    (limits, changeSet.copy(droppedSpeedLimitIds = changeSet.droppedSpeedLimitIds ++ limitsToDrop))
  }

  private def generateUnknownSpeedLimitsForLink(roadLink: RoadLinkForSpeedLimit, segmentsOnLink: Seq[SpeedLimit]): Seq[SpeedLimit] = {
    val lrmPositions: Seq[(Double, Double)] = segmentsOnLink.map { x => (x.startMeasure, x.endMeasure) }
    val remainders = lrmPositions.foldLeft(Seq((0.0, roadLink.length)))(GeometryUtils.subtractIntervalFromIntervals).filter { case (start, end) => math.abs(end - start) > MaxAllowedMValueError}
    remainders.map { segment =>
      val geometry = GeometryUtils.truncateGeometry(roadLink.geometry, segment._1, segment._2)
      SpeedLimit(0, roadLink.mmlId, SideCode.BothDirections, roadLink.trafficDirection, None, geometry, segment._1, segment._2, None, None, None, None)
    }
  }

  def fillTopology(roadLinks: Seq[RoadLinkForSpeedLimit], speedLimits: Map[Long, Seq[SpeedLimit]]): (Seq[SpeedLimit], SpeedLimitChangeSet) = {
    val speedLimitSegments: Seq[SpeedLimit] = speedLimits.values.flatten.toSeq
    val fillOperations: Seq[(RoadLinkForSpeedLimit, Seq[SpeedLimit], SpeedLimitChangeSet) => (Seq[SpeedLimit], SpeedLimitChangeSet)] = Seq(
      dropRedundantSegments,
      adjustSegmentMValues,
      adjustSegmentSideCodes,
      dropShortLimits
    )

    val initialChangeSet = SpeedLimitChangeSet(dropSpeedLimitsWithEmptySegments(speedLimits), Nil, Nil, Nil)
    val (fittedSpeedLimitSegments: Seq[SpeedLimit], changeSet: SpeedLimitChangeSet) =
      roadLinks.foldLeft(Seq.empty[SpeedLimit], initialChangeSet) { case (acc, roadLink) =>
        val (existingSegments, changeSet) = acc
        val segments = speedLimitSegments.filter(_.mmlId == roadLink.mmlId)
        val validSegments = segments.filterNot { segment => changeSet.droppedSpeedLimitIds.contains(segment.id) }

        val (adjustedSegments, segmentAdjustments) = fillOperations.foldLeft(validSegments, changeSet) { case ((currentSegments, currentAdjustments), operation) =>
          operation(roadLink, currentSegments, currentAdjustments)
        }

        val generatedSpeedLimits = generateUnknownSpeedLimitsForLink(roadLink, adjustedSegments)
        val unknownLimits = generatedSpeedLimits.map(_ => UnknownLimit(roadLink.mmlId, roadLink.municipalityCode, roadLink.administrativeClass))
        (existingSegments ++ adjustedSegments ++ generatedSpeedLimits,
          segmentAdjustments.copy(generatedUnknownLimits = segmentAdjustments.generatedUnknownLimits ++ unknownLimits))
      }

    val (generatedLimits, existingLimits) = fittedSpeedLimitSegments.partition(_.id == 0)
    (existingLimits ++ generatedLimits, changeSet)
  }
}
