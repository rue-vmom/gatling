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
import io.gatling.datadog.types._

abstract class DataDogPathPattern(runMessage: RunMessage, configuration: GatlingConfiguration) {

  def usersPath(scenario: String): DataDogPath
  def responsePath(requestName: String, groups: List[String]): DataDogPath

  def metrics(userBreakdowns: Map[DataDogPath, UserBreakdown], responseMetricsByStatus: Map[DataDogPath, MetricByStatus]): Iterator[(DataDogPath, Long)] = {
    val userMetrics = userBreakdowns.iterator.flatMap(byProgress)
    val targetResponseMetrics = responseMetricsByStatus.iterator
    val responseMetrics = targetResponseMetrics.flatMap(byStatus).flatMap(byMetric)

    (userMetrics ++ responseMetrics)
  }

  private def byProgress(metricsEntry: (DataDogPath, UserBreakdown)): Seq[(DataDogPath, Long)] = {
    val (path, usersBreakdown) = metricsEntry
    Seq(
      activeUsers(path) -> usersBreakdown.active,
      waitingUsers(path) -> usersBreakdown.waiting,
      doneUsers(path) -> usersBreakdown.done
    )
  }

  private def byStatus(metricsEntry: (DataDogPath, MetricByStatus)): Seq[(DataDogPath, Option[Metrics])] = {
    val (path, metricByStatus) = metricsEntry
    Seq(
      okResponses(path) -> metricByStatus.ok,
      koResponses(path) -> metricByStatus.ko
    )
  }

  private def byMetric(metricsEntry: (DataDogPath, Option[Metrics])): Seq[(DataDogPath, Long)] =
    metricsEntry match {
      case (path, None) => Seq(count(path) -> 0)
      case (path, Some(m)) =>
        Seq(
          count(path) -> m.count,
          min(path) -> m.min,
          max(path) -> m.max,
          mean(path) -> m.mean,
          stdDev(path) -> m.stdDev,
          percentiles1(path) -> m.percentile1,
          percentiles2(path) -> m.percentile2,
          percentiles3(path) -> m.percentile3,
          percentiles4(path) -> m.percentile4
        )
    }

  protected def metricRootPath: DataDogPath
  protected def activeUsers(path: DataDogPath): DataDogPath
  protected def waitingUsers(path: DataDogPath): DataDogPath
  protected def doneUsers(path: DataDogPath): DataDogPath
  protected def okResponses(path: DataDogPath): DataDogPath
  protected def koResponses(path: DataDogPath): DataDogPath
  protected def count(path: DataDogPath): DataDogPath
  protected def min(path: DataDogPath): DataDogPath
  protected def max(path: DataDogPath): DataDogPath
  protected def mean(path: DataDogPath): DataDogPath
  protected def stdDev(path: DataDogPath): DataDogPath
  protected def percentiles1(path: DataDogPath): DataDogPath
  protected def percentiles2(path: DataDogPath): DataDogPath
  protected def percentiles3(path: DataDogPath): DataDogPath
  protected def percentiles4(path: DataDogPath): DataDogPath
}
