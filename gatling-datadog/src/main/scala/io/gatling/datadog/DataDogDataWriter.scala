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

import scala.collection.mutable

import io.gatling.commons.util.Clock
import io.gatling.commons.util.Collections._
import io.gatling.core.config.GatlingConfiguration
import io.gatling.core.stats.message.ResponseTimings
import io.gatling.core.stats.writer._
import io.gatling.core.util.NameGen
import io.gatling.datadog.message._
import io.gatling.datadog.sender.MetricsSender
import io.gatling.datadog.types._

import akka.actor.ActorRef

final case class DataDogData(
    metricsSender:   ActorRef,
    requestsByPath:  mutable.Map[DataDogPath, RequestMetricsBuffer],
    usersByScenario: mutable.Map[DataDogPath, UserBreakdownBuffer],
    format:          DataDogPathPattern
) extends DataWriterData

private[gatling] class DataDogDataWriter(clock: Clock, configuration: GatlingConfiguration) extends DataWriter[DataDogData] with NameGen {

  def newResponseMetricsBuffer: RequestMetricsBuffer =
    new HistogramRequestMetricsBuffer(configuration)

  private val flushTimerName = "flushTimer"

  def onInit(init: Init): DataDogData = {
    import init._

    val metricsSender: ActorRef = context.actorOf(MetricsSender.props(clock, configuration), genName("metricsSender"))
    val requestsByPath = mutable.Map.empty[DataDogPath, RequestMetricsBuffer]
    val usersByScenario = mutable.Map.empty[DataDogPath, UserBreakdownBuffer]

    val pattern: DataDogPathPattern = new OldDataDogPathPattern(runMessage, configuration)

    scenarios.foreach(scenario => usersByScenario += (pattern.usersPath(scenario.name) -> new UserBreakdownBuffer(scenario.totalUserCount.getOrElse(0L))))

    setTimer(flushTimerName, Flush, configuration.data.datadog.writePeriod, repeat = true)

    DataDogData(metricsSender, requestsByPath, usersByScenario, pattern)
  }

  def onFlush(data: DataDogData): Unit = {
    import data._

    val requestsMetrics = requestsByPath.mapValues(_.metricsByStatus).toMap
    val usersBreakdowns = usersByScenario.mapValues(_.breakDown).toMap

    // Reset all metrics
    requestsByPath.foreach { case (_, buff) => buff.clear() }

    sendMetricsToDataDog(data, requestsMetrics, usersBreakdowns)
  }

  private def onUserMessage(userMessage: UserMessage, data: DataDogData): Unit = {
    import data._
    usersByScenario(format.usersPath(userMessage.session.scenario)).add(userMessage)
  }

  private def onResponseMessage(response: ResponseMessage, data: DataDogData): Unit = {
    import data._
    import response._
    val responseTime = ResponseTimings.responseTime(startTimestamp, endTimestamp)
    requestsByPath.getOrElseUpdate(format.responsePath(name, groupHierarchy), newResponseMetricsBuffer).add(status, responseTime)
  }

  override def onMessage(message: LoadEventMessage, data: DataDogData): Unit = message match {
    case user: UserMessage         => onUserMessage(user, data)
    case response: ResponseMessage => onResponseMessage(response, data)
    case _                         =>
  }

  override def onCrash(cause: String, data: DataDogData): Unit = {}

  def onStop(data: DataDogData): Unit = {
    cancelTimer(flushTimerName)
    onFlush(data)
  }

  private def sendMetricsToDataDog(
    data:            DataDogData,
    requestsMetrics: Map[DataDogPath, MetricByStatus],
    userBreakdowns:  Map[DataDogPath, UserBreakdown]
  ): Unit = {

    import data._

    format.metrics(userBreakdowns, requestsMetrics).foreach {
      case (path, value) => {
        logger.debug(s"Path=${path}")

        if (path.lift(2).getOrElse("") == "users") {
          if (path.lift(0).getOrElse("") == "waiting") {
            metricsSender ! RecordGaugeValue(
              "user.waiting",
              value,
              Array(
                "simulation:" + path.lift(3).getOrElse("unknown"),
                "scenario:" + path.lift(1).getOrElse("unknown")
              )
            )
          }
          if (path.lift(0).getOrElse("") == "active") {
            metricsSender ! RecordGaugeValue(
              "user.active",
              value,
              Array(
                "simulation:" + path.lift(3).getOrElse("unknown"),
                "scenario:" + path.lift(1).getOrElse("unknown")
              )
            )
          }
          if (path.lift(0).getOrElse("") == "done") {
            metricsSender ! RecordGaugeValue(
              "user.done",
              value,
              Array(
                "simulation:" + path.lift(3).getOrElse("unknown"),
                "scenario:" + path.lift(1).getOrElse("done")
              )
            )
          }
        }
        if (path.lift(0).getOrElse("") == "count") {
          metricsSender ! RecordGaugeValue(
            "request.count",
            value,
            Array(
              "simulation:" + path.lift(3).getOrElse("unknown"),
              "is_success:" + (if (path.lift(1).getOrElse("ko") == "ok") "true" else "false"),
              "target:" + path.lift(2).getOrElse("unknown")
            )
          )
        }
        if (path.lift(0).getOrElse("") == "min") {
          metricsSender ! RecordGaugeValue(
            "request.min",
            value,
            Array(
              "simulation:" + path.lift(3).getOrElse("unknown"),
              "target:" + path.lift(2).getOrElse("unknown")
            )
          )
        }
        if (path.lift(0).getOrElse("") == "max") {
          metricsSender ! RecordGaugeValue(
            "request.max",
            value,
            Array(
              "simulation:" + path.lift(3).getOrElse("unknown"),
              "target:" + path.lift(2).getOrElse("unknown")
            )
          )
        }
        if (path.lift(0).getOrElse("") == "mean") {
          metricsSender ! RecordGaugeValue(
            "request.mean",
            value,
            Array(
              "simulation:" + path.lift(3).getOrElse("unknown"),
              "target:" + path.lift(2).getOrElse("unknown")
            )
          )
        }
        if (path.lift(0).getOrElse("") == "stdDev") {
          metricsSender ! RecordGaugeValue(
            "request.stdDev",
            value,
            Array(
              "simulation:" + path.lift(3).getOrElse("unknown"),
              "target:" + path.lift(2).getOrElse("unknown")
            )
          )
        }
        if (path.lift(0).getOrElse("") == "percentiles50") {
          metricsSender ! RecordGaugeValue(
            "request.p50",
            value,
            Array(
              "simulation:" + path.lift(3).getOrElse("unknown"),
              "target:" + path.lift(2).getOrElse("unknown")
            )
          )
        }
        if (path.lift(0).getOrElse("") == "percentiles75") {
          metricsSender ! RecordGaugeValue(
            "request.p75",
            value,
            Array(
              "simulation:" + path.lift(3).getOrElse("unknown"),
              "target:" + path.lift(2).getOrElse("unknown")
            )
          )
        }
        if (path.lift(0).getOrElse("") == "percentiles95") {
          metricsSender ! RecordGaugeValue(
            "request.p95",
            value,
            Array(
              "simulation:" + path.lift(3).getOrElse("unknown"),
              "target:" + path.lift(2).getOrElse("unknown")
            )
          )
        }
        if (path.lift(0).getOrElse("") == "percentiles99") {
          metricsSender ! RecordGaugeValue(
            "request.p99",
            value,
            Array(
              "simulation:" + path.lift(3).getOrElse("unknown"),
              "target:" + path.lift(2).getOrElse("unknown")
            )
          )
        }
      }
    }
  }
}
