package fi.liikennevirasto.viite.util

import fi.liikennevirasto.digiroad2.asset.SideCode
import fi.liikennevirasto.digiroad2.linearasset.{PolyLine, RoadLinkLike}
import fi.liikennevirasto.digiroad2.util.Track
import fi.liikennevirasto.digiroad2.{GeometryUtils, Point}
import fi.liikennevirasto.viite._
import fi.liikennevirasto.viite.dao.{Discontinuity, LinkStatus, ProjectLink}

/**
  * Split suravage link together with project link template
  */
object ProjectLinkSplitter {
  private def isDirectionReversed(link1: PolyLine, link2: PolyLine) = {
    GeometryUtils.areAdjacent(link1.geometry.head, link2.geometry.last, MaxDistanceForConnectedLinks) ||
      GeometryUtils.areAdjacent(link1.geometry.last, link2.geometry.head, MaxDistanceForConnectedLinks)
  }
  private def suravageWithOptions(suravage: ProjectLink, templateLink: ProjectLink, split: SplitOptions, suravageM: Double,
                              splitAddressM: Long) = {
    (
      suravage.copy(roadNumber = split.roadNumber,
        roadPartNumber = split.roadPartNumber,
        track = Track.apply(split.trackCode),
        discontinuity = Discontinuity.Continuous,
        //          ely = split.ely,
        roadType = RoadType.apply(split.roadType),
        startMValue = 0.0,
        endMValue = suravageM,
        startAddrMValue = templateLink.startAddrMValue,
        endAddrMValue = splitAddressM,
        status = split.statusA,
        sideCode = templateLink.sideCode,
        roadAddressId = templateLink.roadAddressId,
        connectedLinkId = Some(templateLink.linkId),
        geometry = GeometryUtils.truncateGeometry2D(suravage.geometry, 0.0, suravageM),
        geometryLength = suravageM
      ),
      suravage.copy(roadNumber = split.roadNumber,
        roadPartNumber = split.roadPartNumber,
        track = Track.apply(split.trackCode),
        discontinuity = Discontinuity.apply(split.discontinuity),
        //          ely = split.ely,
        roadType = RoadType.apply(split.roadType),
        startMValue = suravageM,
        endMValue = suravage.geometryLength,
        startAddrMValue = splitAddressM,
        endAddrMValue = templateLink.endAddrMValue,
        status = split.statusB,
        sideCode = templateLink.sideCode,
        roadAddressId = templateLink.roadAddressId,
        connectedLinkId = Some(templateLink.linkId),
        geometry = GeometryUtils.truncateGeometry2D(suravage.geometry, suravageM, suravage.geometryLength),
        geometryLength = suravage.geometryLength - suravageM)
    )
  }
  def split(suravage: ProjectLink, templateLink: ProjectLink, split: SplitOptions): Seq[ProjectLink] = {
    def movedFromStart(suravageM: Double, templateM: Double, splitAddressM: Long) = {
      val (splitA, splitB) = suravageWithOptions(suravage, templateLink, split, suravageM, splitAddressM)
      val splitT = templateLink.copy(
        startMValue = templateM,
        endMValue = templateLink.geometryLength,
        geometryLength = templateLink.geometryLength - templateM,
        startAddrMValue = splitAddressM,
        status = LinkStatus.Terminated,
        geometry = GeometryUtils.truncateGeometry2D(templateLink.geometry, templateM, templateLink.geometryLength),
        connectedLinkId = Some(suravage.linkId)
      )
      (splitA,splitB,splitT)
    }
    def movedFromEnd(suravageM: Double, templateM: Double, splitAddressM: Long) = {
      val (splitA, splitB) = suravageWithOptions(suravage, templateLink, split, suravageM, splitAddressM)
      val splitT = templateLink.copy(
        startMValue = 0.0,
        endMValue = templateM,
        geometryLength = templateM,
        startAddrMValue = splitAddressM,
        status = LinkStatus.Terminated,
        geometry = GeometryUtils.truncateGeometry2D(templateLink.geometry, 0.0, templateM),
        connectedLinkId = Some(suravage.linkId))
      (splitA,splitB,splitT)
    }
    def switchDigitization(splits: (ProjectLink, ProjectLink, ProjectLink)) = {
      val (splitA, splitB, splitT) = splits
      (
        splitB.copy(
          sideCode = SideCode.switch(splitA.sideCode),
          startMValue = 0.0,
          endMValue = splitB.startMValue),
        splitA.copy(
          sideCode = SideCode.switch(splitA.sideCode),
          startMValue = splitB.startMValue,
          endMValue = splitB.endMValue
        ),
        splitT)
    }
    def toSeq(splits: (ProjectLink, ProjectLink, ProjectLink)) = {
      Seq(splits._1, splits._2, splits._3)
    }
    val suravageM = GeometryUtils.calculateLinearReferenceFromPoint(split.splitPoint, suravage.geometry)
    val templateM = GeometryUtils.calculateLinearReferenceFromPoint(split.splitPoint, templateLink.geometry)
    val splitAddressM = templateLink.startAddrMValue + Math.round(templateM / templateLink.geometryLength *
      (templateLink.endAddrMValue - templateLink.startAddrMValue))
    val splits =
      if (split.statusB == LinkStatus.New)
        movedFromStart(suravageM, templateM, splitAddressM)
      else
        movedFromEnd(suravageM, templateM, splitAddressM)
    if (isDirectionReversed(suravage, templateLink))
      toSeq(switchDigitization(splits))
    else
      toSeq(splits)

  }

  def findMatchingGeometrySegment(suravage: RoadLinkLike, template: RoadLinkLike): Option[Seq[Point]] = {
    def findMatchingSegment(suravageGeom: Seq[Point], templateGeom: Seq[Point]): Option[Seq[Point]] = {
      if (GeometryUtils.areAdjacent(suravageGeom.head, templateGeom.head, MaxDistanceForConnectedLinks)) {
        val boundaries = geometryToBoundaries(suravageGeom)
        val templateSegments = GeometryUtils.geometryToSegments(templateGeom)
        val exitPoint = templateSegments.flatMap { seg =>
          findIntersection(seg, boundaries._1, Some(Epsilon), Some(Epsilon))
            .orElse(findIntersection(seg, boundaries._2, Some(Epsilon), Some(Epsilon)))
        }.headOption
        if (exitPoint.nonEmpty) {
          // Exits the tunnel -> find point
          exitPoint.map(ep => GeometryUtils.truncateGeometry2D(templateGeom, 0.0,
            GeometryUtils.calculateLinearReferenceFromPoint(ep, templateGeom)))
        } else {
          Some(GeometryUtils.truncateGeometry2D(templateGeom, 0.0, Math.max(template.length, suravage.length)))
        }
      } else if (GeometryUtils.areAdjacent(suravageGeom.last, templateGeom.head, MaxDistanceForConnectedLinks)) {
        findMatchingSegment(suravageGeom.reverse, templateGeom)
      } else
        None
    }
    findMatchingSegment(suravage.geometry, template.geometry).orElse(
      findMatchingSegment(suravage.geometry.reverse, template.geometry.reverse).map(_.reverse))
  }

  def findIntersection(geometry1: Seq[Point], geometry2: Seq[Point], maxDistance1: Option[Double] = None,
                       maxDistance2: Option[Double] = None): Option[Point] = {
    val segments1 = geometry1.zip(geometry1.tail)
    val segments2 = geometry2.zip(geometry2.tail)
    val s = segments1.flatMap( s1 => segments2.flatMap{ s2 =>
      intersectionPoint(s1, s2).filter(p =>
        maxDistance1.forall(d => GeometryUtils.minimumDistance(p, s1) < d) &&
          maxDistance2.forall(d => GeometryUtils.minimumDistance(p, s2) < d))})
    s.headOption
  }

  def intersectionPoint(segment1: (Point, Point), segment2: (Point, Point)): Option[Point] = {

    def switchXY(p: Point) = {
      Point(p.y, p.x, p.z)
    }
    val ((p1,p2),(p3,p4)) = (segment1, segment2)
    val v1 = p2-p1
    val v2 = p4-p3
    if (Math.abs(v1.x) < 0.001) {
      if (Math.abs(v2.x) < 0.001) {
        // Both are vertical or near vertical -> swap x and y and recalculate
        return intersectionPoint((switchXY(p1),switchXY(p2)), (switchXY(p3), switchXY(p4))).map(switchXY)
      } else {
        val dx = (p1 - p3).x
        val normV2 = v2.normalize2D()
        return Some(p3 + normV2.scale(dx/normV2.x))
      }
    } else if (Math.abs(v2.x) < 0.001) {
      // second parameter is near vertical, switch places and rerun
      return intersectionPoint((switchXY(p3), switchXY(p4)), (switchXY(p1),switchXY(p2))).map(switchXY)
    }

    val a = v1.y / v1.x
    val b = p1.y - a * p1.x
    val c = v2.y / v2.x
    val d = p3.y - c * p3.x
    if (Math.abs(a-c) < 1E-4 && Math.abs(d-b) > 1E-4) {
      // Differing y is great but coefficients a and c are almost same -> Towards infinities
      None
    } else {
      val x = (d - b) / (a - c)
      val y = a * x + b
      if (x.isNaN || x.isInfinity || y.isNaN || y.isInfinity)
        None
      else
        Some(Point(x, y))
    }
  }

  def geometryToBoundaries(suravageGeometry: Seq[Point]): (Seq[Point], Seq[Point]) = {
    def connectSegments(unConnected: Seq[(Point, Point)]): Seq[Point] = {
      unConnected.scanLeft(unConnected.head){ case (previous, current) =>
        val intersection = intersectionPoint(previous, current)
        (intersection.getOrElse(current._1), current._2)
      }.map(_._1) ++ Seq(unConnected.last._2)
    }
    val (leftUnConnected, rightUnConnected) = suravageGeometry.zip(suravageGeometry.tail).map{ case (p1, p2) =>
      val vecL = (p2-p1).normalize2D().rotateLeft().scale(MaxSuravageToleranceToGeometry)
      ((p1 + vecL, p2 + vecL), (p1 - vecL, p2 - vecL))
    }.unzip
    (connectSegments(leftUnConnected).tail, connectSegments(rightUnConnected).tail)
  }
}

case class SplitOptions(splitPoint: Point, statusA: LinkStatus, statusB: LinkStatus,
                        roadNumber: Long, roadPartNumber: Long, trackCode: Int, discontinuity: Int, ely: Long,
                        roadLinkSource: Int, roadType: Int)

