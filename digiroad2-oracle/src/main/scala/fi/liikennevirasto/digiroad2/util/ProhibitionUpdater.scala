package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.asset.{Prohibition, UnknownLinkType}
import fi.liikennevirasto.digiroad2.client.vvh.ChangeInfo
import fi.liikennevirasto.digiroad2.dao.Queries
import fi.liikennevirasto.digiroad2.linearasset.LinearAssetFiller._
import fi.liikennevirasto.digiroad2.linearasset.{PersistedLinearAsset, Prohibitions, RoadLink}
import fi.liikennevirasto.digiroad2.service.linearasset.{LinearAssetTypes, Measures, ProhibitionService}

class ProhibitionUpdater(service: ProhibitionService) extends LinearAssetUpdater(service) {

  override def updateByRoadLinks(typeId: Int, municipality: Int, roadLinks: Seq[RoadLink], changes: Seq[ChangeInfo]) = {
    try {
      val linkIds = roadLinks.map(_.linkId)
      val mappedChanges = LinearAssetUtils.getMappedChanges(changes)
      val removedLinkIds = LinearAssetUtils.deletedRoadLinkIds(mappedChanges, linkIds.toSet)
      val existingAssets = dao.fetchProhibitionsByLinkIds(typeId, linkIds ++ removedLinkIds, includeFloating = false).filterNot(_.expired)

      val timing = System.currentTimeMillis

      val (assetsOnChangedLinks, assetsWithoutChangedLinks) = existingAssets.partition(a => LinearAssetUtils.newChangeInfoDetected(a, mappedChanges))
      val projectableTargetRoadLinks = roadLinks.filter(
        rl => rl.linkType.value == UnknownLinkType.value || rl.isCarTrafficRoad)

      val initChangeSet = ChangeSet(droppedAssetIds = Set.empty[Long],
        expiredAssetIds = existingAssets.filter(asset => removedLinkIds.contains(asset.linkId)).map(_.id).toSet.filterNot(_ == 0L),
        adjustedMValues = Seq.empty[MValueAdjustment],
        adjustedVVHChanges = Seq.empty[VVHChangesAdjustment],
        adjustedSideCodes = Seq.empty[SideCodeAdjustment],
        valueAdjustments = Seq.empty[ValueAdjustment])

      val combinedAssets = existingAssets.filterNot(a => assetsWithoutChangedLinks.exists(_.id == a.id))

      val (newAssets, changedSet) = fillNewRoadLinksWithPreviousAssetsData(projectableTargetRoadLinks,
        combinedAssets, assetsOnChangedLinks, changes, initChangeSet, existingAssets)

      if (newAssets.nonEmpty) {
        logger.info("Transferred %d assets in %d ms ".format(newAssets.length, System.currentTimeMillis - timing))
      }
      val groupedAssets = (existingAssets.filterNot(a => newAssets.exists(_.linkId == a.linkId)) ++ newAssets ++ assetsWithoutChangedLinks).groupBy(_.linkId)
      val (filledTopology, changeSet) = assetFiller.fillTopology(roadLinks, groupedAssets, typeId, Some(changedSet))
      updateChangeSet(changeSet)

      persistProjectedLinearAssets(newAssets.filter(_.id == 0))

      logger.info(s"Updated asset $typeId in municipality $municipality.")
    } catch {
      case e => logger.error(s"Updating asset $typeId in municipality $municipality failed due to ${e.getMessage}.")
    }
  }

  override def persistProjectedLinearAssets(newLinearAssets: Seq[PersistedLinearAsset]): Unit = {
    val (toInsert, toUpdate) = newLinearAssets.partition(_.id == 0L)
    val roadLinks = roadLinkService.getRoadLinksAndComplementariesFromVVH(newLinearAssets.map(_.linkId).toSet, newTransaction = false)
    if (toUpdate.nonEmpty) {
      val prohibitions = toUpdate.filter(a => Set(Prohibition.typeId).contains(a.typeId))
      val persisted = dao.fetchProhibitionsByIds(Prohibition.typeId, prohibitions.map(_.id).toSet).groupBy(_.id)
      updateProjected(toUpdate, persisted)
      if (newLinearAssets.nonEmpty)
        logger.info("Updated ids/linkids " + toUpdate.map(a => (a.id, a.linkId)))
    }
    toInsert.foreach { linearAsset =>
      val id =
        (linearAsset.createdBy, linearAsset.createdDateTime) match {
          case (Some(createdBy), Some(createdDateTime)) =>
            dao.createLinearAsset(linearAsset.typeId, linearAsset.linkId, linearAsset.expired, linearAsset.sideCode,
              Measures(linearAsset.startMeasure, linearAsset.endMeasure), LinearAssetTypes.VvhGenerated, linearAsset.vvhTimeStamp,
              service.getLinkSource(roadLinks.find(_.linkId == linearAsset.linkId)), true, Some(createdBy), Some(createdDateTime), linearAsset.verifiedBy, linearAsset.verifiedDate)
          case _ =>
            dao.createLinearAsset(linearAsset.typeId, linearAsset.linkId, linearAsset.expired, linearAsset.sideCode,
              Measures(linearAsset.startMeasure, linearAsset.endMeasure), LinearAssetTypes.VvhGenerated, linearAsset.vvhTimeStamp, service.getLinkSource(roadLinks.find(_.linkId == linearAsset.linkId)))
        }
      linearAsset.value match {
        case Some(prohibitions: Prohibitions) =>
          dao.insertProhibitionValue(id, Prohibition.typeId, prohibitions)
        case _ => None
      }
    }
    if (newLinearAssets.nonEmpty)
      logger.info("Added assets for linkids " + toInsert.map(_.linkId))
  }

  override def adjustedSideCode(adjustment: SideCodeAdjustment): Unit = {
    val oldAsset = service.getPersistedAssetsByIds(adjustment.typeId, Set(adjustment.assetId), newTransaction = false).headOption
      .getOrElse(throw new IllegalStateException("Prohibition: Old asset " + adjustment.assetId + " no longer available"))
    val roadLink = roadLinkService.getRoadLinkAndComplementaryFromVVH(oldAsset.linkId, newTransaction = false)
      .getOrElse(throw new IllegalStateException("Road link " + oldAsset.linkId + " no longer available"))
    service.expireAsset(oldAsset.typeId, oldAsset.id, LinearAssetTypes.VvhGenerated, expired = true, newTransaction = false)
    service.createWithoutTransaction(oldAsset.typeId, oldAsset.linkId, oldAsset.value.get, adjustment.sideCode.value,
      Measures(oldAsset.startMeasure, oldAsset.endMeasure), LinearAssetTypes.VvhGenerated, roadLinkClient.roadLinkData.createVVHTimeStamp(),
      Some(roadLink), false, Some(LinearAssetTypes.VvhGenerated), None, oldAsset.verifiedBy, oldAsset.informationSource.map(_.value))
  }

  override protected def updateProjected(toUpdate: Seq[PersistedLinearAsset], persisted: Map[Long, Seq[PersistedLinearAsset]]) = {
    def valueChanged(assetToPersist: PersistedLinearAsset, persistedLinearAsset: Option[PersistedLinearAsset]) = {
      !persistedLinearAsset.exists(_.value == assetToPersist.value)
    }

    toUpdate.foreach { linearAsset =>
      val persistedLinearAsset = persisted.getOrElse(linearAsset.id, Seq()).headOption
      val id = linearAsset.id
      if (valueChanged(linearAsset, persistedLinearAsset)) {
        linearAsset.value match {
          case Some(prohibitions: Prohibitions) =>
            dao.updateProhibitionValue(id, linearAsset.typeId, prohibitions, LinearAssetTypes.VvhGenerated)
          case _ => None
        }
      }
    }
  }

}
