package fi.liikennevirasto.digiroad2.csvDataImporter

import com.github.tototoshi.csv.{CSVReader, DefaultCSVFormat}
import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.asset.{DateParser, SideCode}
import fi.liikennevirasto.digiroad2.lane._
import fi.liikennevirasto.digiroad2.middleware.{AdditionalImportValue, UpdateOnlyStartDates}
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.service.lane.LaneService
import fi.liikennevirasto.digiroad2.user.User
import fi.liikennevirasto.digiroad2.util.ChangeLanesAccordingToVvhChanges.updateChangeSet
import fi.liikennevirasto.digiroad2.util.LaneUtils.getRoadAddressToProcess
import fi.liikennevirasto.digiroad2.util.{LaneUtils, LogUtils, RoadAddressException, Track}
import org.apache.commons.lang3.StringUtils.isBlank
import org.slf4j.LoggerFactory

import java.io.{InputStream, InputStreamReader}
import scala.util.{Success, Try}


class LanesCsvImporter(roadLinkServiceImpl: RoadLinkService, eventBusImpl: DigiroadEventBus) extends CsvDataImporter(roadLinkServiceImpl: RoadLinkService, eventBusImpl: DigiroadEventBus) {
  case class NotImportedData(reason: String, csvRow: String)
  case class ImportResultLaneAsset(incompleteRows: List[IncompleteRow] = Nil,
                                   malformedRows: List[MalformedRow] = Nil,
                                   excludedRows: List[ExcludedRow] = Nil,
                                   notImportedData: List[NotImportedData] = Nil,
                                   createdData: List[ParsedProperties] = Nil)  extends ImportResult
  type ImportResultData = ImportResultLaneAsset
  type ParsedCsv = (MalformedParameters, List[ParsedProperties])
  def laneUtils = LaneUtils()
  lazy val laneService: LaneService = new LaneService(roadLinkServiceImpl, eventBusImpl, roadAddressService)
  lazy val laneFiller: LaneFiller = new LaneFiller
  private val csvImportUser = "csv_importer"
  val logger = LoggerFactory.getLogger(getClass)

  private val nonMandatoryFieldsMapping: Map[String, String] = Map(
    "id" -> "id",
    "tietyyppi" -> "road type",
    "s_tietyyppi" -> "name road type",
    "s_katyyppi" -> "name lane type"
  )

  private val intValueFieldsMapping: Map[String, String] = Map(
    "tie" -> "road number",
    "ajorata" -> "track",
    "osa" -> "road part",
    "aet" -> "initial distance",
    "let" -> "end distance"
  )


  private val laneNumberFieldMapping: Map[String, String] = Map("kaista" -> "lane")
  private val laneTypeFieldMapping: Map[String, String] = Map("katyyppi" -> "lane type")
  private val dateFieldMapping: Map[String, String] = Map("alkupvm" -> "start date")

  val mandatoryFieldsMapping: Map[String, String] = laneNumberFieldMapping ++ intValueFieldsMapping ++ laneTypeFieldMapping

  private def findMissingParameters(csvRowWithHeaders: Map[String, String]): List[String] = {
    mandatoryFieldsMapping.keySet.diff(csvRowWithHeaders.keys.toSet).toList
  }

  def getPropertyValue(pointAssetAttributes: ParsedProperties, propertyName: String): String = {
    pointAssetAttributes.find(prop => prop.columnName == propertyName).map(_.value).get.asInstanceOf[String]
  }

  def getPropertyValueOption(pointAssetAttributes: ParsedProperties, propertyName: String): Option[String] = {
    pointAssetAttributes.find(prop => prop.columnName == propertyName).map(_.value).asInstanceOf[Option[String]]
  }

  def verifyDateType(parameterName: String, parameterValue: String): ParsedRow = {
    val formattedValue = parameterValue.trim.replaceAll("[/-]", ".")

    val isDateParserOk = Try(DateParser.stringToDate(formattedValue, DateParser.DatePropertyFormat))

    if (isDateParserOk.isSuccess) {
      (Nil, List(AssetProperty(columnName = dateFieldMapping(parameterName), value = formattedValue)))
    } else {
      (List(parameterName), Nil)
    }

  }

  def verifyIntType(parameterName: String, parameterValue: String): ParsedRow = {
    if (parameterValue.forall(_.isDigit)) {
      (Nil, List(AssetProperty(columnName = intValueFieldsMapping(parameterName), value = parameterValue)))
    } else {
      (List(parameterName), Nil)
    }
  }

  def verifyLaneNumber(parameterName: String, parameterValue: String): ParsedRow = {
    val trimmedValue = parameterValue.trim

    Try(trimmedValue.toInt) match {
                case Success(value) if (LaneNumber.isValidLaneNumber(value)) =>
                  (Nil, List(AssetProperty(columnName = laneNumberFieldMapping(parameterName), value = trimmedValue)))

                case _ =>
                  (List(parameterName), Nil)
    }
  }

  def verifyLaneType(parameterName: String, parameterValue: String): ParsedRow = {
    if (parameterValue.forall(_.isDigit) && LaneType.apply(parameterValue.toInt) != LaneType.Unknown) {
      (Nil, List(AssetProperty(columnName = laneTypeFieldMapping(parameterName), value = parameterValue)))
    } else {
      (List(parameterName), Nil)
    }
  }

  def startDateRequiredOnLane(csvRowWithHeaders: Map[String, String]): Boolean = {
    val laneCodeValue = csvRowWithHeaders.getOrElse("kaista", "")
    val laneCode = if (laneCodeValue.trim.nonEmpty) laneCodeValue.toInt else 0
    !LaneNumber.isMainLane(laneCode)
  }

  def assetRowToAttributes(csvRowWithHeaders: Map[String, String]): ParsedRow = {
    csvRowWithHeaders.foldLeft(Nil: MalformedParameters, Nil: ParsedProperties) {
      (result, parameter) =>
        val (key, value) = parameter

        if (isBlank(value)) {
          if (mandatoryFieldsMapping.contains(key))
            result.copy(_1 = List(key) ::: result._1, _2 = result._2)
          else if (nonMandatoryFieldsMapping.contains(key))
            result.copy(_2 = AssetProperty(columnName = nonMandatoryFieldsMapping(key), value = value) :: result._2)
          else if (dateFieldMapping.contains(key) && startDateRequiredOnLane(csvRowWithHeaders))
            result.copy(_1 = List(key) ::: result._1, _2 = result._2)
          else
            result
        } else {
          if (intValueFieldsMapping.contains(key)) {
            val (malformedParameters, properties) = verifyIntType(key, value)
            result.copy(_1 = malformedParameters ::: result._1, _2 = properties ::: result._2)
          } else if (laneNumberFieldMapping.contains(key)) {
            val (malformedParameters, properties) = verifyLaneNumber(key, value)
            result.copy(_1 = malformedParameters ::: result._1, _2 = properties ::: result._2)
          } else if (laneTypeFieldMapping.contains(key)) {
            val (malformedParameters, properties) = verifyLaneType(key, value)
            result.copy(_1 = malformedParameters ::: result._1, _2 = properties ::: result._2)
          }else if(mandatoryFieldsMapping.contains(key)) {
            result.copy(_2 = AssetProperty(columnName = mandatoryFieldsMapping(key), value = value) :: result._2)
          } else if (nonMandatoryFieldsMapping.contains(key)) {
            result.copy(_2 = AssetProperty(columnName = nonMandatoryFieldsMapping(key), value = value) :: result._2)
          } else if ( dateFieldMapping.contains(key) ){
            val (malformedParameters, properties) = verifyDateType(key, value)
            result.copy(_1 = malformedParameters ::: result._1, _2 = properties ::: result._2)
          }else {
            result
          }
        }
    }
  }

  def verifyData(parsedRow: ParsedProperties, updateOnlyStartDates: Boolean): ParsedCsv = {
    val optTrack = getPropertyValueOption(parsedRow, "track")
    val optLane = getPropertyValueOption(parsedRow, "lane")

    (optTrack, optLane) match {
      case (Some(track), Some(lane)) =>
        (Track.apply(track.toInt), lane.charAt(0).getNumericValue) match {
          case (Track.RightSide, 2) | (Track.LeftSide, 1)  =>
            (List(s"Wrong lane number for the track given"), List())
          case (_, _) if lane.charAt(1).getNumericValue == 1 && !updateOnlyStartDates =>
            (List(s"Not allowed to import main lanes"), List())
          case (_, _) => (List(), List(parsedRow))
        }
      case _ =>
        (Nil, Nil)
    }
  }

  override def mappingContent(result: ImportResultData): String = {
    val excludedResult = result.excludedRows.map { rows => s"<li> ${rows.affectedRows} -> ${rows.csvRow} </li>" }
    val incompleteResult = result.incompleteRows.map { rows => s"<li> ${rows.missingParameters.mkString(";")} -> ${rows.csvRow} </li>" }
    val malformedResult = result.malformedRows.map { rows => s"<li> ${rows.malformedParameters.mkString(";")}  -> ${rows.csvRow} </li>" }
    val notImportedData = result.notImportedData.map { rows => "<li>" + rows.reason -> rows.csvRow + "</li>" }

    s"<ul> excludedLinks: ${excludedResult.mkString.replaceAll("[(|)]{1}", "")} </ul>" +
    s"<ul> incompleteRows: ${incompleteResult.mkString.replaceAll("[(|)]{1}", "")} </ul>" +
    s"<ul> malformedRows: ${malformedResult.mkString.replaceAll("[(|)]{1}", "")} </ul>" +
    s"<ul> notImportedData: ${notImportedData.mkString.replaceAll("[(|)]{1}", "")}</ul>"
  }

  def giveMainlanesStartDates(laneAssetProperties: Seq[ParsedProperties], user: User, result: ImportResultData): ImportResultData = {
      val lanesToUpdateAndMissingLanes = laneAssetProperties.map(props => {
        val roadNumber = getPropertyValue(props, "road number").toLong
        val roadPartNumber = getPropertyValue(props, "road part").toLong
        val laneCode = getPropertyValue(props, "lane")

        val initialDistance = getPropertyValue(props, "initial distance").toLong
        val endDistance = getPropertyValue(props, "end distance").toLong
        val track = getPropertyValue(props, "track").toInt
        val startDate = getPropertyValueOption(props, "start date").getOrElse("")

        val laneProps = Seq(LaneProperty("start_date", Seq(LanePropertyValue(startDate))))

        val laneRoadAddressInfo = LaneRoadAddressInfo(roadNumber, roadPartNumber, initialDistance, roadPartNumber, endDistance, track)
        val roadAddresses = LogUtils.time(logger, "Get roadAddresses for road: " + roadNumber + "part: " + roadPartNumber){
          getRoadAddressToProcess(laneRoadAddressInfo).flatMap(_.addresses)
        }

        val roadLinksFullyInsideRoadAddressRange = roadAddresses.filter(address =>
          address.startAddressM >= laneRoadAddressInfo.startDistance && address.endAddressM <= laneRoadAddressInfo.endDistance)
        val roadLinksPartiallyInsideRoadAddressRange = roadAddresses.filter(address =>
          (address.startAddressM >= laneRoadAddressInfo.startDistance && address.endAddressM <= laneRoadAddressInfo.endDistance) ||
           address.endAddressM <= laneRoadAddressInfo.endDistance && address.endAddressM >= laneRoadAddressInfo.startDistance)
        val filteredRoadAddresses = (roadLinksFullyInsideRoadAddressRange ++ roadLinksPartiallyInsideRoadAddressRange)

        val roadLinks = roadLinkService.getRoadLinksByLinkIdsFromVVH(filteredRoadAddresses.map(_.linkId), false)
        val lanes = laneService.fetchAllLanesByLinkIds(filteredRoadAddresses.map(_.linkId).toSeq, false)
        val lanesAndRoadLink = lanes.flatMap(lane => {
          val roadLink = roadLinks.find(_.linkId == lane.linkId)
          roadLink match {
            case Some(rl) => Some((lane, rl))
            case _ => None
          }
        })
        val pwLanes = lanesAndRoadLink.flatMap(pair => {
          val lane = pair._1
          val roadLink = pair._2
          laneFiller.toLPieceWiseLane(Seq(lane), roadLink)

        })

        val twoDigitPwLanes = LogUtils.time(logger, "Transform " + pwLanes.size + " lanes to two digit lanes with mass query"){
            laneService.pieceWiseLanesToTwoDigitWithMassQuery(pwLanes)
        }
        val twoDigitLanes = laneService.pieceWiseLanesToPersistedLane(twoDigitPwLanes.flatten)
        //Lanes where VKM failed to transform coordinates to road addresses
        val missingLanes = lanes.map(_.id).diff(twoDigitLanes.map(_.id))

        val correctLanes = twoDigitLanes.filter(_.laneCode == laneCode.toInt)
        if (missingLanes.nonEmpty){
          (correctLanes.map(_.copy(attributes = laneProps)), missingLanes, Some(props))
        }
        else (correctLanes.map(_.copy(attributes = laneProps)), missingLanes, None)
      })

    val lanesToUpdate = lanesToUpdateAndMissingLanes.flatMap(_._1)
    val notUpdatedLanes = lanesToUpdateAndMissingLanes.flatMap(_._2).toSet
    val failedRows = lanesToUpdateAndMissingLanes.flatMap(_._3)
    laneService.updateMultipleLaneAttributes(lanesToUpdate, user.username)


    notUpdatedLanes.isEmpty match {
      case true => result
      case false =>
        val failedRowsMessage = failedRows.map(props => props.map(prop => {
          prop.columnName + " " + prop.value.toString
        })).mkString("\n")
        val notImported = result.notImportedData ++ List(NotImportedData(" Something went wrong on rows: " + failedRowsMessage +
          " Not updated lane ids: " + notUpdatedLanes.mkString("\n"), "" ))
        result.copy(notImportedData = notImported)
    }
  }

  def createAsset(laneAssetProperties: Seq[ParsedProperties], user: User, result: ImportResultData): (ImportResultData, Set[Long]) = {
    val createdLaneIds = laneAssetProperties.groupBy(lane => getPropertyValue(lane, "road number")).flatMap { lane =>
      lane._2.flatMap { props =>
        val roadPartNumber = getPropertyValue(props, "road part").toLong
        val laneCode = getPropertyValue(props, "lane")

        val initialDistance = getPropertyValue(props, "initial distance").toLong
        val endDistance = getPropertyValue(props, "end distance").toLong
        val track = getPropertyValue(props, "track").toInt
        val laneType = getPropertyValue(props, "lane type").toInt

        val startDate = getPropertyValueOption(props, "start date").getOrElse("")
        val isMainLane = LaneNumber.isMainLane(laneCode.toInt)

        val sideCode = track match {
          case 1 | 2 => SideCode.BothDirections
          case _ => if (laneCode.charAt(0).getNumericValue == 1) SideCode.TowardsDigitizing else SideCode.AgainstDigitizing
        }

        val laneProps = Seq(LaneProperty("lane_code", Seq(LanePropertyValue(laneCode))),
                        LaneProperty("lane_type", Seq(LanePropertyValue(laneType))))
        val properties = if (!isMainLane && startDate.nonEmpty) laneProps ++ Seq(LaneProperty("start_date", Seq(LanePropertyValue(startDate)))) else laneProps

        //id, start measure, end measure and municipalityCode doesnt matter
        val incomingLane = NewLane(0, 0, 0, 0, isExpired = false, isDeleted = false, properties)
        val laneRoadAddressInfo = LaneRoadAddressInfo(lane._1.toLong, roadPartNumber, initialDistance, roadPartNumber, endDistance, track)
        laneUtils.processNewLanesByRoadAddress(Set(incomingLane), laneRoadAddressInfo, sideCode.value, user.username, false)
      }
    }.toSet

    (result, createdLaneIds)
  }

  def importAssets(inputStream: InputStream, fileName: String, user: User, logId: Long, updateOnlyStartDates: AdditionalImportValue): Unit = {
    try {
      val result = processing(inputStream, user, updateOnlyStartDates.asInstanceOf[UpdateOnlyStartDates])
      result match {
        case ImportResultLaneAsset(Nil, Nil, Nil, Nil, _) => update(logId, Status.OK)
        case _ =>
          val content = mappingContent(result)
          update(logId, Status.NotOK, Some(content))
      }
    } catch {
      case e: Exception =>
        update(logId, Status.Abend, Some("Lähettäessä tapahtui odottamaton virhe: " + e.toString))
    } finally {
      inputStream.close()
    }
  }

  def processing(inputStream: InputStream, user: User, updateOnlyStartDates: UpdateOnlyStartDates): ImportResultData = {
    val streamReader = new InputStreamReader(inputStream, "UTF-8")
    val csvReader = CSVReader.open(streamReader)(new DefaultCSVFormat {
      override val delimiter: Char = ';'
    })

    withDynTransaction {
      val result = csvReader.allWithHeaders().foldLeft(ImportResultLaneAsset()) {
        (result, row) =>
          val csvRow = row.map(r => (r._1.toLowerCase(), r._2))
          val missingParameters = findMissingParameters(csvRow)
          val (malformedParameters, properties) = assetRowToAttributes(csvRow)
          val (notImportedParameters, parsedRow) = verifyData(properties, updateOnlyStartDates.onlyStartDates)

          if (missingParameters.nonEmpty || malformedParameters.nonEmpty || notImportedParameters.nonEmpty) {
            result.copy(
              incompleteRows = missingParameters match {
                case Nil => result.incompleteRows
                case parameters =>
                  IncompleteRow(missingParameters = parameters, csvRow = rowToString(csvRow)) :: result.incompleteRows
              },
              malformedRows = malformedParameters match {
                case Nil => result.malformedRows
                case parameters =>
                  MalformedRow(malformedParameters = parameters, csvRow = rowToString(csvRow)) :: result.malformedRows
              },
              notImportedData = notImportedParameters match {
                case Nil => result.notImportedData
                case parameters =>
                  NotImportedData(reason = parameters.head, csvRow = rowToString(csvRow)) :: result.notImportedData
              })
          } else {
            result.copy(createdData = parsedRow ++ result.createdData)
          }
      }

      updateOnlyStartDates.onlyStartDates match {
        case true => giveMainlanesStartDates(result.createdData, user, result)
        case false =>
          // Expire all additional lanes IF exists some data to create new lanes
          if (result.createdData.nonEmpty) {
            laneService.expireAllAdditionalLanes(csvImportUser)
          }

          // Create the new lanes
          val createdLaneIds = createAsset(result.createdData, user, result)._2


          //Lanes created from CSV-import have to be processed through fill topology to combine overlapping lanes etc.
          if (createdLaneIds.nonEmpty) {
            val createdLanes = laneService.getPersistedLanesByIds(createdLaneIds, false)
            val groupedLanes = createdLanes.groupBy(_.linkId)
            if (groupedLanes.isEmpty) result
            else {
              val linkIds = createdLanes.map(_.linkId).toSet
              val roadLinks = roadLinkService.getRoadLinksByLinkIdsFromVVH(linkIds, false)
              val changeSet = laneFiller.fillTopology(roadLinks, groupedLanes)._2

              //For reasons unknown fillTopology creates duplicate mValue adjustments for some lanes and
              // tries to expire new lanes when used with lanes created by CSV-import, so we have to filter them out
              val newLanesFilteredFromMValueAdj = changeSet.adjustedMValues.filterNot(_.laneId == 0)
              val duplicatesFilteredFromMValueAdj = newLanesFilteredFromMValueAdj.groupBy(_.laneId).map(_._2.head).toSeq
              val newLanesFilteredFromExpiredIds = changeSet.expiredLaneIds.filterNot(_ == 0)
              val newLanesFilteredFromVVHAdj = changeSet.adjustedVVHChanges.filterNot(_.laneId == 0)
              val newLanesFilteredFromSideCodeAdj = changeSet.adjustedSideCodes.filterNot(_.laneId == 0)
              val changeSetFixed = changeSet.copy(adjustedMValues = duplicatesFilteredFromMValueAdj,
                expiredLaneIds = newLanesFilteredFromExpiredIds, adjustedVVHChanges = newLanesFilteredFromVVHAdj, adjustedSideCodes = newLanesFilteredFromSideCodeAdj)

              updateChangeSet(changeSetFixed)
              result
            }

          }
          else result
      }
    }
  }
}
