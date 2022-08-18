package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.asset.LinkGeomSource.NormalLinkInterface
import fi.liikennevirasto.digiroad2.Point
import fi.liikennevirasto.digiroad2.asset.{SideCode, TrafficDirection}
import fi.liikennevirasto.digiroad2.client.vvh.ChangeInfo
import fi.liikennevirasto.digiroad2.linearasset.{SpeedLimit, SpeedLimitValue}
import org.joda.time.DateTime
import org.scalatest.{FunSuite, Matchers}

/**
  * Created by venholat on 30.3.2016.
  */
class LinearAssetUtilsSpec extends FunSuite with Matchers {

  test("testNewChangeInfoDetected") {
    // current time stamp of the asset is older
    val linkId1 = "1"
    val linkId2 = "2"
    val speedlimit = SpeedLimit(1, linkId1, SideCode.BothDirections,TrafficDirection.BothDirections,Some(SpeedLimitValue(40)),
      Seq(Point(0.0,0.0),Point(1.0,0.0)), 0.0, 1.0, None, None, None, None, 14000000, None, linkSource = NormalLinkInterface)
    // current time stamp of the asset is the same
    val speedlimit2 = SpeedLimit(1, linkId1, SideCode.BothDirections,TrafficDirection.BothDirections,Some(SpeedLimitValue(50)),
      Seq(Point(0.0,0.0),Point(1.0,0.0)), 0.0, 1.0, None, None, None, None, 15000000, None, linkSource = NormalLinkInterface)
    // no change info for this link
    val speedlimit3 = SpeedLimit(1, linkId2, SideCode.BothDirections,TrafficDirection.BothDirections,Some(SpeedLimitValue(60)),
      Seq(Point(0.0,0.0),Point(1.0,0.0)), 0.0, 1.0, None, None, None, None, 14000000, None, linkSource = NormalLinkInterface)
    val changeinfo = Seq(ChangeInfo(Some(linkId1), Some(linkId1), 1, 9, Some(0), Some(1), Some(1), Some(0), 15000000))
    LinearAssetUtils.newChangeInfoDetected(speedlimit, changeinfo) should be (true)
    LinearAssetUtils.newChangeInfoDetected(speedlimit2, changeinfo) should be (false)
    LinearAssetUtils.newChangeInfoDetected(speedlimit3, changeinfo) should be (false)
  }

  test("timestamp is correctly created") {
    val hours = DateTime.now().getHourOfDay
    val yesterday = LinearAssetUtils.createTimeStamp(hours + 1)
    val today = LinearAssetUtils.createTimeStamp(hours)

    (today % 24*60*60*1000L) should be (0L)
    (yesterday % 24*60*60*1000L) should be (0L)
    today should be > yesterday
    (yesterday + 24*60*60*1000L) should be (today)
  }
}
