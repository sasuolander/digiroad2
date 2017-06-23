package fi.liikennevirasto.viite.process

import fi.liikennevirasto.digiroad2.{ChangeInfo, Point}
import fi.liikennevirasto.digiroad2.linearasset.RoadLink
import fi.liikennevirasto.digiroad2.user.UserProvider
import fi.liikennevirasto.viite.RoadAddressService
import fi.liikennevirasto.viite.dao.{RoadAddress, RoadAddressDAO}

object RoadAddressChangeInfoMapper extends RoadAddressMapper {

  private def createAddressMap(sources: Seq[ChangeInfo]): Seq[Any] = {
    val pseudoGeom = Seq(Point(0.0, 0.0), Point(1.0, 0.0))
    sources.map(ci => {
          ci.changeType match {
            case 1 => {
              RoadAddressMapping(ci.oldId.get, ci.newId.get, ci.oldStartMeasure.get, ci.oldEndMeasure.get,
              ci.newStartMeasure.get, ci.newEndMeasure.get, pseudoGeom, pseudoGeom, Some(ci.vvhTimeStamp))
              println("Change info> oldId: "+ci.oldId+" newId: "+ci.newId+" changeType: "+ci.changeType)
            }
            case 2 => {
              RoadAddressMapping(ci.oldId.get, ci.newId.get, ci.oldStartMeasure.get, ci.oldEndMeasure.get,
                ci.newStartMeasure.get, ci.newEndMeasure.get, pseudoGeom, pseudoGeom, Some(ci.vvhTimeStamp))
              println("Change info> oldId: "+ci.oldId+" newId: "+ci.newId+" changeType: "+ci.changeType)
            }
            case _ => None
          }
        }).filter(c => !c.equals(None))
  }

  private def applyChanges(changes: Seq[Seq[ChangeInfo]], roadAddresses: Map[Long, Seq[RoadAddress]]): Map[Long, Seq[RoadAddress]] = {
    if (changes.isEmpty)
      roadAddresses
    else {
      val mapping = createAddressMap(changes.head).asInstanceOf[Seq[RoadAddressMapping]]
      val mapped = roadAddresses.mapValues(_.flatMap(ra =>
        if (mapping.exists(_.matches(ra))) {
          val changeVVHTimestamp = mapping.filter(m => m.sourceLinkId == ra.linkId || m.targetLinkId == ra.linkId).map(_.vvhTimeStamp.get).sortWith(_ > _).head
          mapRoadAddresses(mapping)(ra).map(_.copy(adjustedTimestamp = changeVVHTimestamp))
        }
        else
          Seq(ra)))
      applyChanges(changes.tail, mapped.values.toSeq.flatten.groupBy(_.linkId))
    }
  }

  def resolveChangesToMap(roadAddresses: Map[Long, Seq[RoadAddress]], changedRoadLinks: Seq[RoadLink], changes: Seq[ChangeInfo]): Map[Long, Seq[RoadAddress]] = {
    val groupedChanges = changes.groupBy(_.vvhTimeStamp).values.toSeq
    val appliedChanges = applyChanges(groupedChanges.sortBy(_.head.vvhTimeStamp), roadAddresses)
    appliedChanges.map(change =>
      change._1 -> appliedChanges(change._1).toList)
  }
}
