package fi.liikennevirasto.digiroad2.service.pointasset.masstransitstop

import java.util.NoSuchElementException

import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.asset.{AdministrativeClass, BoundingRectangle, SimpleProperty}
import fi.liikennevirasto.digiroad2.dao.{MassTransitStopDao, Sequences, ServicePoint, ServicePointBusStopDao}
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.user.User

class ServicePointBusStopService(typeId : Int, servicePointBusStopDao: ServicePointBusStopDao = new ServicePointBusStopDao, massTransitStopDao: MassTransitStopDao = new MassTransitStopDao) {

  def withDynSession[T](f: => T): T = OracleDatabase.withDynSession(f)
  private val validityDirectionPublicId = "vaikutussuunta"

  def create(asset: NewMassTransitStop, username: String, point: Point, municipalityCode: Int): (ServicePoint, AbstractPublishInfo) = {
    val assetId = Sequences.nextPrimaryKeySeqValue
    val nationalId = massTransitStopDao.getNationalBusStopId
    val newAssetPoint = Point(asset.lon, asset.lat)
    massTransitStopDao.insertAsset(assetId, nationalId, newAssetPoint.x, newAssetPoint.y, username, municipalityCode, floating = false)

    val defaultValues = massTransitStopDao.propertyDefaultValues(typeId).filterNot(defaultValue => asset.properties.exists(_.publicId == defaultValue.publicId))
    if (asset.properties.find(p => p.publicId == MassTransitStopOperations.MassTransitStopTypePublicId).get.values.exists(v => v.propertyValue != MassTransitStopOperations.ServicePointBusStopPropertyValue))
      throw new IllegalArgumentException

    massTransitStopDao.updateAssetProperties(assetId, asset.properties.filterNot(p =>  p.publicId == validityDirectionPublicId || p.publicId == "liitetyt_pysakit") ++ defaultValues.toSet)
    val resultAsset = fetchAsset(assetId)
    (resultAsset.get,PublishInfo(None))
  }

  def fetchAsset(id: Long): Option[ServicePoint] = {
    servicePointBusStopDao.fetchAsset(massTransitStopDao.withIdAndNotExpired(id)).headOption
  }

  def fetchAssetByNationalId(id: Long): Option[ServicePoint] = {
    servicePointBusStopDao.fetchAsset(massTransitStopDao.withNationalIdAndNotExpired(id)).headOption
  }

  def delete(asset: ServicePoint, username: String): Option[AbstractPublishInfo] = {
    servicePointBusStopDao.expire(asset.id, username)
    None
  }

  def update(asset: ServicePoint, properties: Set[SimpleProperty], username: String, municipalityValidation: (Int, AdministrativeClass) => Unit): (ServicePoint, AbstractPublishInfo) = {

    if (asset.propertyData.find(p => p.publicId == MassTransitStopOperations.MassTransitStopTypePublicId).get.values.exists(v => v.propertyValue != MassTransitStopOperations.ServicePointBusStopPropertyValue))
      throw new IllegalArgumentException

    municipalityValidation(asset.municipalityCode, AdministrativeClass(99))

    //Enrich properties with old administrator, if administrator value is empty in CSV import
    val verifiedProperties = MassTransitStopOperations.getVerifiedProperties(properties, asset.propertyData)

    val id = asset.id
    massTransitStopDao.updateAssetLastModified(id, username)
    massTransitStopDao.updateAssetProperties(id, verifiedProperties.filterNot(p =>  p.publicId == validityDirectionPublicId).toSeq)

    val resultAsset = enrichBusStop(fetchAsset(id).get)._1
    (resultAsset, PublishInfo(None))
  }

  def enrichBusStop(asset: ServicePoint): (ServicePoint, Boolean) = {
    (asset.copy(propertyData = asset.propertyData), false)
  }

  def withFilter(filter: String)(query: String): String = {
    query + " " + filter
  }

  def getByBoundingBox(user: User, bounds: BoundingRectangle) : Seq[ServicePoint] = {
    withDynSession {
      val boundingBoxFilter = OracleDatabase.boundingBoxFilter(bounds, "a.geometry")
      val filter = s"where a.asset_type_id = $typeId and $boundingBoxFilter"
      servicePointBusStopDao.fetchAsset(withFilter(filter))
    }
  }
}
