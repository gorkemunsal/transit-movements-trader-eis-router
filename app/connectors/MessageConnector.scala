/*
 * Copyright 2021 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package connectors

import com.google.inject.Inject
import config.AppConfig
import models.RoutingOption
import models.RoutingOption.Gb
import models.RoutingOption.Xi
import play.api.Logging
import play.api.http.Status
import uk.gov.hmrc.http.Authorization
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.HttpClient
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.HttpResponse

import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.xml.NodeSeq

class MessageConnector @Inject() (config: AppConfig, http: HttpClient)(implicit
  ec: ExecutionContext
) extends Logging {

  private case class EisDetails(url: String, token: String, routingMessage: String)

  def post(xml: NodeSeq, routingOption: RoutingOption, hc: HeaderCarrier): Future[HttpResponse] = {
    val details = routingOption match {
      case Xi => EisDetails(config.eisniUrl, config.eisniBearerToken, "routing to NI")
      case Gb => EisDetails(config.eisgbUrl, config.eisgbBearerToken, "routing to GB")
    }

    val requestHeaders = hc.headers(OutgoingHeaders.headers) ++ Seq(
      "X-Correlation-Id"  -> UUID.randomUUID().toString,
      "CustomProcessHost" -> "Digital"
    )

    implicit val headerCarrier = hc
      .copy(authorization = Some(Authorization(s"Bearer ${details.token}")))
      .withExtraHeaders(requestHeaders: _*)

    def getHeader(header: String): String =
      headerCarrier
        .headers(Seq(header))
        .collectFirst {
          case (requestHeader, headerValue) if header.toLowerCase == requestHeader.toLowerCase =>
            headerValue
        }
        .getOrElse("undefined")

    http.POSTString[HttpResponse](details.url, xml.toString).map { result =>
      lazy val logMessage =
        s"""|Posting NCTS message, ${details.routingMessage}
              |X-Correlation-ID: ${getHeader("X-Correlation-Id")}
              |X-Request-ID: ${getHeader("X-Request-Id")}
              |X-Message-Type: ${getHeader("X-Message-Type")}
              |X-Message-Sender: ${getHeader("X-Message-Sender")}
              |Response status: ${result.status}
              """.stripMargin

      if (Status.isServerError(result.status) || result.status == Status.FORBIDDEN)
        logger.warn(logMessage)
      else
        logger.info(logMessage)

      result
    }
  }
}
