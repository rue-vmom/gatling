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

package io.gatling.datadog.sender

import java.net.InetSocketAddress

import scala.concurrent.duration._

import io.gatling.commons.util.Clock
import io.gatling.core.akka.BaseActor
import io.gatling.core.config._

import akka.actor.{ Props, Stash }

import com.timgroup.statsd.{ NonBlockingStatsDClient }

private[datadog] object MetricsSender {

  def props(clock: Clock, configuration: GatlingConfiguration): Props = {
    val statsDClient = new NonBlockingStatsDClient(
      configuration.data.datadog.rootPathPrefix,
      configuration.data.datadog.host,
      configuration.data.datadog.port,
      Array("galting:foo"): _*
    )

    Props(new DogStatsdSender(statsDClient))
  }
}

private[datadog] abstract class MetricsSender extends BaseActor with Stash
