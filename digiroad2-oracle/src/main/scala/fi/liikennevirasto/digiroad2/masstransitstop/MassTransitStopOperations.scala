package fi.liikennevirasto.digiroad2.masstransitstop

import fi.liikennevirasto.digiroad2.asset.{AbstractProperty, SimpleProperty, _}
import fi.liikennevirasto.digiroad2.{FloatingReason, PersistedMassTransitStop, VVHRoadlink}

/**
  * Created by venholat on 2.11.2016.
  */
object MassTransitStopOperations {
  val StateOwned: Set[AdministrativeClass] = Set(State)
  val OtherOwned: Set[AdministrativeClass] = Set(Municipality, Private)
  val CommuterBusStopPropertyValue: String = "2"
  val LongDistanceBusStopPropertyValue: String = "3"
  val VirtualBusStopPropertyValue: String = "5"
  val MassTransitStopTypePublicId = "pysakin_tyyppi"
  val MassTransitStopAdminClassPublicId = "linkin_hallinnollinen_luokka"

  val CentralELYPropertyValue = "2"
  val HSLPropertyValue = "3"
  val AdminClassStatePropertyValue = "1"
  val AdministratorInfoPublicId = "tietojen_yllapitaja"
  val LiViIdentifierPublicId = "yllapitajan_koodi"
  val InventoryDateId = "inventointipaiva"

  val nameFiPublicId = "nimi_suomeksi"
  val nameSePublicId = "nimi_ruotsiksi"

  /**
    * Check for administrative class change: road link has differing owner other than Unknown.
    *
    * @param administrativeClass MassTransitStop administrative class
    * @param roadLinkAdminClassOption RoadLink administrative class
    * @return true, if mismatch (owner known and non-compatible)
    */
  def administrativeClassMismatch(administrativeClass: AdministrativeClass,
                                  roadLinkAdminClassOption: Option[AdministrativeClass]) = {
    val rlAdminClass = roadLinkAdminClassOption.getOrElse(Unknown)
    val mismatch = StateOwned.contains(rlAdminClass) && OtherOwned.contains(administrativeClass) ||
      OtherOwned.contains(rlAdminClass) && StateOwned.contains(administrativeClass)
    administrativeClass != Unknown && roadLinkAdminClassOption.nonEmpty && mismatch
  }

  def isFloating(administrativeClass: AdministrativeClass, roadLinkOption: Option[VVHRoadlink]): (Boolean, Option[FloatingReason]) = {

    val roadLinkAdminClass = roadLinkOption.map(_.administrativeClass)
    if (administrativeClassMismatch(administrativeClass, roadLinkAdminClass))
      (true, Some(FloatingReason.RoadOwnerChanged))
    else
      (false, None)
  }

  def floatingReason(administrativeClass: AdministrativeClass, roadLink: VVHRoadlink): Option[String] = {
    if (administrativeClassMismatch(administrativeClass, Some(roadLink.administrativeClass)))
      Some("Road link administrative class has changed from %d to %d".format(roadLink.administrativeClass.value, administrativeClass.value))
    else
      None
  }


  /**
    * Verify if the stop is relevant to Tierekisteri: Must be non-virtual and must be administered by ELY.
    * Convenience method
    *
    * @param persistedStopOption The persisted stops
    * @return returns true if the stop is not virtual and is a ELY bus stop
    */
  def isStoredInTierekisteri(persistedStopOption: Option[PersistedMassTransitStop]): Boolean ={
    persistedStopOption match {
      case Some(persistedStop) =>
        isStoredInTierekisteri(persistedStop.propertyData)
      case _ =>
        false
    }
  }

  /**
    * Verify if the stop is relevant to Tierekisteri: Must be non-virtual and must be administered by ELY or HSL.
    * Convenience method
    *
    */
  def isStoredInTierekisteri(properties: Seq[AbstractProperty]): Boolean ={
    val administrationProperty = properties.find(_.publicId == AdministratorInfoPublicId)
    val stopType = properties.find(pro => pro.publicId == MassTransitStopTypePublicId)
    val adminClass = properties.find(pro => pro.publicId == MassTransitStopAdminClassPublicId)
    isStoredInTierekisteri(administrationProperty, stopType, adminClass)
  }

  def isStoredInTierekisteri(administratorInfo: Option[AbstractProperty], stopTypeProperty: Option[AbstractProperty],
                             adminClassProperty: Option[AbstractProperty]) = {
    val elyAdministrated = administratorInfo.exists(_.values.headOption.exists(_.propertyValue == CentralELYPropertyValue))
    val isVirtualStop = stopTypeProperty.exists(_.values.exists(_.propertyValue == VirtualBusStopPropertyValue))
    val isAdminClassState = adminClassProperty.exists(_.values.exists(_.propertyValue == AdminClassStatePropertyValue))
    val isHSLAdministrated =  administratorInfo.exists(_.values.headOption.exists(_.propertyValue == HSLPropertyValue))
    !isVirtualStop && (elyAdministrated || (isHSLAdministrated && isAdminClassState))
  }

  def liviIdValueOption(properties: Seq[AbstractProperty]) = {
    properties.find(_.publicId == LiViIdentifierPublicId).flatMap(prop => prop.values.headOption)
  }

  def liviIdValue(properties: Seq[AbstractProperty]) = {
    liviIdValueOption(properties).get
  }

  /**
    * Checks that virtualTransitStop doesn't have any other types selected
    *
    * @param stopProperties Properties of the mass transit stop
    * @return true, if any stop types included with a virtual stop type
    */
  def mixedStoptypes(stopProperties: Set[SimpleProperty]): Boolean =
  {
    val propertiesSelected = stopProperties.filter(p => MassTransitStopTypePublicId.equalsIgnoreCase(p.publicId))
      .flatMap(_.values).map(_.propertyValue)
    propertiesSelected.contains(VirtualBusStopPropertyValue) && propertiesSelected.exists(!_.equals(VirtualBusStopPropertyValue))
  }

}
