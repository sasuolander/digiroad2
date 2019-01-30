package fi.liikennevirasto.digiroad2.dataimport

import java.io.InputStreamReader

import fi.liikennevirasto.digiroad2.Digiroad2Context.properties
import fi.liikennevirasto.digiroad2.{DigiroadEventBus, _}
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.authentication.RequestHeaderAuthentication
import fi.liikennevirasto.digiroad2.middleware.{AdministrativeValues, CsvDataImporterInfo}
import fi.liikennevirasto.digiroad2.user.UserProvider
import fi.liikennevirasto.digiroad2.util.MassTransitStopExcelDataImporter
import javax.servlet.ServletException
import org.joda.time.DateTime
import org.json4s.{CustomSerializer, DefaultFormats, Formats, JString}
import org.scalatra._
import org.scalatra.servlet.{FileItem, FileUploadSupport, MultipartConfig}
import org.scalatra.json.JacksonJsonSupport
import fi.liikennevirasto.digiroad2.asset.Asset._

class ImportDataApi extends ScalatraServlet with FileUploadSupport with JacksonJsonSupport with RequestHeaderAuthentication {

  case object DateTimeSerializer extends CustomSerializer[DateTime](format => ( {
    case _ => throw new NotImplementedError("DateTime deserialization")
  }, {
    case d: DateTime => JString(d.toString(DateTimePropertyFormat))
  }))

  protected implicit val jsonFormats: Formats = DefaultFormats + DateTimeSerializer
  private val CSV_LOG_PATH = "/tmp/csv_data_import_logs/"

  lazy val csvDataImporter = new CsvDataImporter
  private final val threeMegabytes: Long = 3*1024*1024
  lazy val user = userProvider.getCurrentUser()
  lazy val eventbus: DigiroadEventBus = {
    Class.forName(properties.getProperty("digiroad2.eventBus")).newInstance().asInstanceOf[DigiroadEventBus]
  }

  lazy val userProvider: UserProvider = {
    Class.forName(properties.getProperty("digiroad2.userProvider")).newInstance().asInstanceOf[UserProvider]
  }

  before() {
    contentType = formats("json")
    configureMultipartHandling(MultipartConfig(maxFileSize = Some(threeMegabytes)))
    try {
      authenticateForApi(request)(userProvider)
    } catch {
      case ise: IllegalStateException => halt(Unauthorized("Authentication error: " + ise.getMessage))
    }
    response.setHeader("Digiroad2-Server-Originated-Response", "true")
  }

  post("/maintenanceRoads") {
    if (!user.isOperator()) {
      halt(Forbidden("Vain operaattori voi suorittaa Excel-ajon"))
    }
   importMaintenanceRoads(fileParams("csv-file"))
  }

  post("/trafficSigns") {
    val municipalitiesToExpire = request.getParameterValues("municipalityNumbers") match {
      case null => Set.empty[Int]
      case municipalities => municipalities.map(_.toInt).toSet
    }

    if (!(user.isOperator() || user.isMunicipalityMaintainer())) {
      halt(Forbidden("Vain operaattori tai kuntaylläpitäjä voi suorittaa Excel-ajon"))
    }

    if (user.isMunicipalityMaintainer() && municipalitiesToExpire.diff(user.configuration.authorizedMunicipalities).nonEmpty) {
      halt(Forbidden(s"Puuttuvat muokkausoikeukset jossain listalla olevassa kunnassa: ${municipalitiesToExpire.mkString(",")}"))
    }

   importTrafficSigns(fileParams("csv-file"), municipalitiesToExpire)
  }

  post("/roadlinks") {
    if (!user.isOperator()) {
      halt(Forbidden("Vain operaattori voi suorittaa Excel-ajon"))
    }

    importRoadLinks(fileParams("csv-file"))
  }

  post("/massTransitStop") {
    if (!user.isOperator()) {
      halt(Forbidden("Vain operaattori voi suorittaa Excel-ajon"))
    }
    val administrativeClassLimitations: Set[AdministrativeClass] = Set(
      params.get("limit-import-to-roads").map(_ => State),
      params.get("limit-import-to-streets").map(_ => Municipality),
      params.get("limit-import-to-private-roads").map(_ => Private)
    ).flatten

    importMassTransitStop(fileParams("csv-file"), administrativeClassLimitations)
  }

  get("/log/:id") {
    params.getAs[Long]("id").flatMap(id =>  csvDataImporter.getById(id)).getOrElse("Logia ei löytynyt.")
  }

  get("/log") {
    Seq(ImportStatusInfo(1, Status.OK, "filename1", Some("oskar"), Some(DateTime.now()), "mass transit stop", Some("this is the text content")),
      ImportStatusInfo(1, Status.OK, "filename1", Some("oskar"), Some(DateTime.now()), "mass transit stop", Some("this is the text content")),
      ImportStatusInfo(1, Status.NotOK, "filename1", Some("oskar"), Some(DateTime.now()), "mass transit stop", Some("this is the text content")),
      ImportStatusInfo(1, Status.InProgress, "filename1", Some("oskar"), Some(DateTime.now()), "mass transit stop", Some("this is the text content")),
      ImportStatusInfo(1, Status.Abend, "filename2", Some("kari"), Some(DateTime.now()), "traffic signs", Some("this is the text content")),
      ImportStatusInfo(1, Status.Unknown, "filename3", Some("erik"), Some(DateTime.now()), "road links", Some("this12312312312132312312tent"))).map(job => Map(
      "id" -> job.id,
      "status" -> job.status.value,
      "fileName" -> job.fileName,
      "createdBy" -> job.createdBy,
      "createdDate" -> job.createdDate,
      "assetType" -> job.logType,
      "content" -> job.content,
      "description" -> job.status.descriptionFi

    )
    )
  }

  //TODO check if this is necessary
  override def isSizeConstraintException(e: Exception) = e match {
    case se: ServletException if se.getMessage.contains("exceeds max filesize") ||
      se.getMessage.startsWith("Request exceeds maxRequestSize") => true
    case _ => false
  }

  //TODO check if this exist
  post("/csv") {
    if (!user.isOperator()) {
      halt(Forbidden("Vain operaattori voi suorittaa Excel-ajon"))
    }
    val csvStream = new InputStreamReader(fileParams("csv-file").getInputStream)
    new MassTransitStopExcelDataImporter().updateAssetDataFromCsvFile(csvStream)
  }

  def importTrafficSigns(csvFileItem: FileItem, municipalitiesToExpire: Set[Int]): Unit = {
    val csvFileInputStream = csvFileItem.getInputStream
    val fileName = csvFileItem.getName
    if (csvFileInputStream.available() == 0) halt(BadRequest("Ei valittua CSV-tiedostoa. Valitse tiedosto ja yritä uudestaan.")) else None

    eventbus.publish("importCSVData", CsvDataImporterInfo(TrafficSigns.layerName, fileName, user.username, csvFileInputStream))

  }

  def importRoadLinks(csvFileItem: FileItem ): Unit = {
    val csvFileInputStream = csvFileItem.getInputStream
    val fileName = csvFileItem.getName
    if (csvFileInputStream.available() == 0) halt(BadRequest("Ei valittua CSV-tiedostoa. Valitse tiedosto ja yritä uudestaan.")) else None

    eventbus.publish("importCSVData", CsvDataImporterInfo("roadLinks", fileName, user.username, csvFileInputStream))
  }

  def importMaintenanceRoads(csvFileItem: FileItem): Unit = {
    val csvFileInputStream = csvFileItem.getInputStream
    val fileName = csvFileItem.getName
    if (csvFileInputStream.available() == 0) halt(BadRequest("Ei valittua CSV-tiedostoa. Valitse tiedosto ja yritä uudestaan.")) else None

    eventbus.publish("importCSVData", CsvDataImporterInfo(MassTransitStopAsset.layerName, fileName, user.username, csvFileInputStream))
  }

  def importMassTransitStop(csvFileItem: FileItem, administrativeClassLimitations: Set[AdministrativeClass]) : Unit = {
    val csvFileInputStream = csvFileItem.getInputStream
    val fileName = csvFileItem.getName

    eventbus.publish("importCSVData", CsvDataImporterInfo(MaintenanceRoadAsset.layerName, fileName, user.username, csvFileInputStream, Some(administrativeClassLimitations.asInstanceOf[AdministrativeValues])))
  }
}