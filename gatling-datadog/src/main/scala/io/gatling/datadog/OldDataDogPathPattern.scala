/*
 * Copyright 2011-2019 GatlingCorp (https://gatling.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.gatling.datadog

import io.gatling.core.config.GatlingConfiguration
import io.gatling.core.stats.writer.RunMessage

class OldDataDogPathPattern(runMessage: RunMessage, configuration: GatlingConfiguration) extends DataDogPathPattern(runMessage, configuration) {

  private def removeDecimalPart(d: Double): String = {
    val i = d.toInt
    if (d == i.toDouble) String.valueOf(i)
    else String.valueOf(d)
  }

  private val usersRootKey = DataDogPath.datadogPath("users")
  private val percentiles1Name = "percentiles" + removeDecimalPart(configuration.charting.indicators.percentile1)
  private val percentiles2Name = "percentiles" + removeDecimalPart(configuration.charting.indicators.percentile2)
  private val percentiles3Name = "percentiles" + removeDecimalPart(configuration.charting.indicators.percentile3)
  private val percentiles4Name = "percentiles" + removeDecimalPart(configuration.charting.indicators.percentile4)

  val metricRootPath = DataDogPath.datadogPath(runMessage.simulationId)

  def usersPath(scenario: String): DataDogPath = metricRootPath / usersRootKey / scenario

  def responsePath(requestName: String, groupHierarchy: List[String]) = metricRootPath / DataDogPath.datadogPath(groupHierarchy.reverse) / requestName

  protected def activeUsers(path: DataDogPath) = path / "active"
  protected def waitingUsers(path: DataDogPath) = path / "waiting"
  protected def doneUsers(path: DataDogPath) = path / "done"
  protected def okResponses(path: DataDogPath) = path / "ok"
  protected def koResponses(path: DataDogPath) = path / "ko"
  protected def count(path: DataDogPath) = path / "count"
  protected def min(path: DataDogPath) = path / "min"
  protected def max(path: DataDogPath) = path / "max"
  protected def mean(path: DataDogPath) = path / "mean"
  protected def stdDev(path: DataDogPath) = path / "stdDev"
  protected def percentiles1(path: DataDogPath) = path / percentiles1Name
  protected def percentiles2(path: DataDogPath) = path / percentiles2Name
  protected def percentiles3(path: DataDogPath) = path / percentiles3Name
  protected def percentiles4(path: DataDogPath) = path / percentiles4Name
}
