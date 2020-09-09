/*
 * Copyright 2020 HM Revenue & Customs
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

import java.time.OffsetDateTime
import java.util.UUID

import com.google.inject.Inject
import config.AppConfig
import connectors.MessageConnector.EisSubmissionResult
import connectors.MessageConnector.EisSubmissionResult._
import models.{ItemId, MessageSender, MessageType, MovementMessageWithStatus, TransitWrapper}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.HttpResponse
import uk.gov.hmrc.http.logging.Authorization
import uk.gov.hmrc.http.logging.SessionId
import uk.gov.hmrc.http.HttpClient
import utils.Format
import uk.gov.hmrc.http.HttpReads.Implicits.readRaw

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class MessageConnector @Inject()(config: AppConfig, http: HttpClient)(implicit ec: ExecutionContext) {

  def post(itemId: ItemId, message: MovementMessageWithStatus, dateTime: OffsetDateTime)(
    implicit headerCarrier: HeaderCarrier
  ): Future[EisSubmissionResult] = {

    val xmlMessage = TransitWrapper(message.message).toString

    val url = config.eisUrl

    lazy val messageSender = MessageSender(itemId, message.messageCorrelationId)

    val newHeaders = headerCarrier
      .copy(authorization = Some(Authorization(s"Bearer ${config.eisBearerToken}")))
      .withExtraHeaders(addHeaders(message.messageType, dateTime, messageSender): _*)

    http
      .POSTString[HttpResponse](url, xmlMessage)(readRaw, hc = newHeaders, implicitly)
      .map {
        case x if x.status == EisSubmissionSuccessful.httpStatus       => EisSubmissionSuccessful
        case x if x.status == ErrorInPayload.httpStatus                => ErrorInPayload
        case x if x.status == VirusFoundOrInvalidToken.httpStatus      => VirusFoundOrInvalidToken
        case x if x.status == DownstreamInternalServerError.httpStatus => DownstreamInternalServerError
        case x                                                         => UnexpectedHttpResponse(x)
      }
  }

  private def addHeaders(messageType: MessageType, dateTime: OffsetDateTime, messageSender: MessageSender)(
    implicit headerCarrier: HeaderCarrier): Seq[(String, String)] =
    Seq(
      "X-Forwarded-Host" -> "mdtp",
      "X-Correlation-ID" -> {
        headerCarrier.sessionId
          .map(x => removePrefix(sessionPrefix, x))
          .getOrElse(UUID.randomUUID().toString)
      },
      "Date"             -> Format.dateFormattedForHeader(dateTime),
      "Content-Type"     -> "application/xml",
      "Accept"           -> "application/xml",
      "X-Message-Type"   -> messageType.toString,
      "X-Message-Sender" -> messageSender.toString
    )

  private val sessionPrefix = "session-"

  private[connectors] def removePrefix(prefix: String, sessionId: SessionId): String =
    sessionId.value.replaceFirst(prefix, "")
}

object MessageConnector {

  sealed abstract class EisSubmissionResult(val httpStatus: Int, asString: String) {
    override def toString: String = s"EisSubmissionResult(code = $httpStatus, and details = " + asString + ")"
  }

  object EisSubmissionResult {
    object EisSubmissionSuccessful extends EisSubmissionResult(202, "EIS Successful Submission")

    sealed abstract class EisSubmissionFailure(httpStatus: Int, asString: String) extends EisSubmissionResult(httpStatus, asString)

    sealed abstract class EisSubmissionRejected(httpStatus: Int, asString: String) extends EisSubmissionFailure(httpStatus, asString)
    object ErrorInPayload                                                          extends EisSubmissionRejected(400, "Message failed schema validation")
    object VirusFoundOrInvalidToken                                                extends EisSubmissionRejected(403, "Virus found, token invalid etc")

    sealed abstract class EisSubmissionFailureDownstream(httpStatus: Int, asString: String) extends EisSubmissionFailure(httpStatus, asString)
    object DownstreamInternalServerError                                                    extends EisSubmissionFailureDownstream(500, "Downstream internal server error")
    case class UnexpectedHttpResponse(httpResponse: HttpResponse)
        extends EisSubmissionFailureDownstream(httpResponse.status, "Unexpected HTTP Response received")
  }
}
