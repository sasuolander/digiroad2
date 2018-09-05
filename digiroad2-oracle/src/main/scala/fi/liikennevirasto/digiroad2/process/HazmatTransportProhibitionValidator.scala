package fi.liikennevirasto.digiroad2.process

import java.sql.SQLException

import fi.liikennevirasto.digiroad2.{GeometryUtils, Point}
import fi.liikennevirasto.digiroad2.asset.{HazmatTransportProhibition, SideCode}
import fi.liikennevirasto.digiroad2.dao.linearasset.OracleLinearAssetDao
import fi.liikennevirasto.digiroad2.dao.pointasset.PersistedTrafficSign
import fi.liikennevirasto.digiroad2.linearasset.{PersistedLinearAsset, Prohibitions, RoadLink}
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.service.pointasset.TrafficSignType
import fi.liikennevirasto.digiroad2.service.pointasset.TrafficSignType.NoVehiclesWithDangerGoods

class HazmatTransportProhibitionValidator extends AssetServiceValidatorOperations {
  override type AssetType = PersistedLinearAsset
  override def assetName: String = "hazmatTransportProhibition"
  override def assetType: Int = HazmatTransportProhibition.typeId
  override val radiusDistance: Int = 50

  lazy val dao: OracleLinearAssetDao = new OracleLinearAssetDao(vvhClient, roadLinkService)

  def withDynTransaction[T](f: => T): T = OracleDatabase.withDynTransaction(f)

  val allowedTrafficSign: Set[TrafficSignType] = Set(TrafficSignType.HazmatProhibitionA, TrafficSignType.HazmatProhibitionB, NoVehiclesWithDangerGoods)

  override def filteredAsset(roadLink: RoadLink, assets: Seq[AssetType], pointOfInterest: Point, distance: Double): Seq[AssetType] = {
    def assetDistance(assets: Seq[AssetType]): (AssetType, Double) =  {
      val (first, _) = GeometryUtils.geometryEndpoints(roadLink.geometry)
      if(GeometryUtils.areAdjacent(pointOfInterest, first)) {
        val nearestAsset = assets.filter(a => a.linkId == roadLink.linkId).minBy(_.startMeasure)
        (nearestAsset, nearestAsset.startMeasure)
      }else{
        val nearestAsset = assets.filter(a => a.linkId == roadLink.linkId).maxBy(_.endMeasure)
        (nearestAsset, GeometryUtils.geometryLength(roadLink.geometry) - nearestAsset.endMeasure)
      }
    }

    val assetOnLink = assets.filter(_.linkId == roadLink.linkId)
    if (assetOnLink.nonEmpty && assetDistance(assetOnLink)._2 + distance <= radiusDistance) {
      Seq(assetDistance(assets)._1)
    } else
      Seq()
  }

  def comparingProhibitionValue(prohibition: PersistedLinearAsset, typeId: Int) : Boolean = {
    prohibition.value match {
      case Some(value) => value.asInstanceOf[Prohibitions].prohibitions.exists(_.typeId == typeId)
      case _ => false
    }
  }

  override def verifyAsset(assets: Seq[AssetType], roadLinks: Seq[RoadLink], trafficSign: PersistedTrafficSign): Set[Inaccurate] = {
    val prohibitions = assets.asInstanceOf[Seq[PersistedLinearAsset]]

    prohibitions.flatMap{ prohibition =>
      val roadLink = roadLinks.find(_.linkId == prohibition.linkId)
      TrafficSignType.apply(getTrafficSignsProperties(trafficSign, "trafficSigns_type").get.propertyValue.toInt) match {

        case TrafficSignType.HazmatProhibitionA => if(!comparingProhibitionValue(prohibition, 24))
          Seq(Inaccurate(Some(prohibition.id), None, roadLink.get.municipalityCode, roadLink.get.administrativeClass)) else Seq()
        case TrafficSignType.HazmatProhibitionB => if(!comparingProhibitionValue(prohibition, 25))
          Seq(Inaccurate(Some(prohibition.id), None, roadLink.get.municipalityCode, roadLink.get.administrativeClass)) else Seq()
        case NoVehiclesWithDangerGoods => Seq()
        case _ => throw new NumberFormatException("Not supported trafficSign on Prohibition asset")
      }
    }.toSet
  }

  override def getAsset(roadLinks: Seq[RoadLink]): Seq[AssetType] = {
    dao.fetchProhibitionsByLinkIds(HazmatTransportProhibition.typeId, roadLinks.map(_.linkId), false)
  }

  override def reprocessRelevantTrafficSigns(assetInfo: AssetValidatorInfo): Unit = {
    val MYSQL_DUPLICATE_PK = 1062

    withDynTransaction {
      inaccurateAssetDAO.deleteInaccurateAssetByIds(assetInfo.oldIds.toSeq)

      val assets = dao.fetchProhibitionsByIds(HazmatTransportProhibition.typeId, assetInfo.newIds)
      val roadLinks = roadLinkService.getRoadLinksAndComplementariesFromVVH(assets.map(_.linkId).toSet, newTransaction = false)

      assets.foreach { asset =>
        val roadLink = roadLinks.find(_.linkId == asset.linkId).getOrElse(throw new NoSuchElementException)
        val assetGeometry = GeometryUtils.truncateGeometry2D(roadLink.geometry, asset.startMeasure, asset.endMeasure)
        val (first, last) = GeometryUtils.geometryEndpoints(assetGeometry)

        val trafficSingsByRadius: Seq[PersistedTrafficSign] = getPointOfInterest(first, last, SideCode.apply(asset.sideCode)).flatMap { position =>
          trafficSignService.getTrafficSignByRadius(position, radiusDistance)
            .filter(sign => allowedTrafficSign.contains(TrafficSignType.apply(getTrafficSignsProperties(sign, "trafficSigns_type").get.propertyValue.toInt)))
        }


        trafficSingsByRadius.foreach { trafficSign =>
          assetValidator(trafficSign).foreach {
            inaccurate =>
              (inaccurate.assetId, inaccurate.linkId) match {
                case (Some(assetId), _) => try {
                  inaccurateAssetDAO.createInaccurateAsset(assetId, assetType, inaccurate.municipalityCode, inaccurate.administrativeClass)
                } catch {
                  case ex: SQLException => if (ex.getErrorCode == MYSQL_DUPLICATE_PK) {
                    print("duplicate key inserted ")
                  } else
                    throw new RuntimeException("Sql exception not defined")
                }
                case (_, Some(linkId)) => try {
                  inaccurateAssetDAO.createInaccurateLink(linkId, assetType, inaccurate.municipalityCode, roadLink.administrativeClass)
                } catch {
                  case ex: SQLException => if (ex.getErrorCode == MYSQL_DUPLICATE_PK) {
                    print("duplicate key inserted ")
                  } else
                    throw new RuntimeException("Sql exception not defined")
                }
                case _ => None
              }
          }
        }
      }
    }
  }
}

