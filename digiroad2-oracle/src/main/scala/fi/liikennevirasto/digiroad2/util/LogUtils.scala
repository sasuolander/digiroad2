package fi.liikennevirasto.digiroad2.util

import org.slf4j.Logger

object LogUtils {
  val timeLoggingThresholdInMs = 0

  def time[R](logger: Logger, operationName: String)(f: => R): R = {
    val begin = System.currentTimeMillis()
    val result = f
    val duration = System.currentTimeMillis() - begin
    if (duration >= timeLoggingThresholdInMs) {
      println(s"$operationName completed in $duration ms")
      logger.info(s"$operationName completed in $duration ms")
    }
    result
  }

}