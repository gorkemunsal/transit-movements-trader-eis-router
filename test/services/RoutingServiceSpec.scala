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

package services

import connectors.MessageConnector
import models.ChannelType
import models.ChannelType.Api
import models.FailureMessage
import models.ParseError.DepartureEmpty
import models.ParseError.InvalidMessageCode
import models.ParseError.PresentationEmpty
import models.RoutingOption
import models.RoutingOption.Gb
import models.RoutingOption.Xi
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.{eq => eqTo}
import org.mockito.Mockito.verify
import org.mockito.Mockito.when
import org.scalacheck.Gen
import org.scalatest.BeforeAndAfterEach
import org.scalatest.OptionValues
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.HttpResponse

import scala.concurrent.Future

class RoutingServiceSpec
    extends AnyFreeSpec
    with Matchers
    with GuiceOneAppPerSuite
    with OptionValues
    with ScalaFutures
    with MockitoSugar
    with BeforeAndAfterEach
    with ScalaCheckDrivenPropertyChecks {

  private def service(
    fsrc: RouteChecker = mock[RouteChecker],
    messageConnector: MessageConnector = mock[MessageConnector]
  ) = new RoutingService(fsrc, messageConnector)

  implicit val hc = HeaderCarrier()

  val channelGen = Gen.oneOf(ChannelType.values)
  val routeGen   = Gen.oneOf(RoutingOption.values)

  "submitMessage" - {

    "returns InvalidMessageCode if message has incorrect message rootNode" in {
      val badXML = <TransitWrapper>
        <ABCDE></ABCDE>
      </TransitWrapper>

      val result = service().submitMessage(badXML, Api, hc)

      result mustBe a[Left[InvalidMessageCode, _]]
    }

    "returns DepartureEmpty if departure message with no office of departure" in {
      val input = <TransitWrapper>
        <CC928A></CC928A>
      </TransitWrapper>

      val result = service().submitMessage(input, Api, hc)

      result mustBe a[Left[DepartureEmpty, _]]
    }

    "returns DestinationEmpty if destination message with no office of presentation" in {
      val input = <TransitWrapper>
        <CC008A></CC008A>
      </TransitWrapper>

      val result = service().submitMessage(input, Api, hc)

      result mustBe a[Left[PresentationEmpty, _]]
    }

    "departure message starting with XI sends XI flag when feature switch is true" in {
      val input = <TransitWrapper>
        <CC015B>
          <CUSOFFDEPEPT>
            <RefNumEPT1>XI12345</RefNumEPT1>
          </CUSOFFDEPEPT>
        </CC015B>
      </TransitWrapper>

      forAll(channelGen) { channel =>
        val mc = mock[MessageConnector]
        when(mc.post(any(), any(), any())).thenReturn(Future.successful(HttpResponse(200, "")))

        val fsrc = mock[RouteChecker]
        when(fsrc.canForward(eqTo(Xi), eqTo(channel))).thenReturn(true)

        service(fsrc, mc).submitMessage(input, channel, hc)

        verify(mc).post(any(), eqTo(Xi), eqTo(hc))
      }
    }

    "departure message starting with XI is rejected feature switch is false" in {
      val input = <TransitWrapper>
        <CC015B>
          <CUSOFFDEPEPT>
            <RefNumEPT1>XI12345</RefNumEPT1>
          </CUSOFFDEPEPT>
        </CC015B>
      </TransitWrapper>

      forAll(channelGen) { channel =>
        val mc = mock[MessageConnector]
        when(mc.post(any(), any(), any())).thenReturn(Future.successful(HttpResponse(200, "")))

        val fsrc = mock[RouteChecker]
        when(fsrc.canForward(eqTo(Xi), eqTo(channel))).thenReturn(false)

        val result = service(fsrc, mc).submitMessage(input, channel, hc)

        result mustBe a[Left[FailureMessage, _]]
      }
    }

    "departure message starting with GB sends Gb flag when feature switch is true" in {
      val input = <TransitWrapper>
        <CC015B>
          <CUSOFFDEPEPT>
            <RefNumEPT1>GB12345</RefNumEPT1>
          </CUSOFFDEPEPT>
        </CC015B>
      </TransitWrapper>

      forAll(channelGen) { channel =>
        val mc = mock[MessageConnector]
        when(mc.post(any(), any(), any())).thenReturn(Future.successful(HttpResponse(200, "")))

        val fsrc = mock[RouteChecker]
        when(fsrc.canForward(eqTo(Gb), eqTo(channel))).thenReturn(true)

        service(fsrc, mc).submitMessage(input, channel, hc)

        verify(mc).post(any(), eqTo(Gb), eqTo(hc))
      }
    }

    "departure message starting with GB is rejected feature switch is false" in {
      val input = <TransitWrapper>
        <CC015B>
          <CUSOFFDEPEPT>
            <RefNumEPT1>GB12345</RefNumEPT1>
          </CUSOFFDEPEPT>
        </CC015B>
      </TransitWrapper>

      forAll(channelGen) { channel =>
        val mc = mock[MessageConnector]
        when(mc.post(any(), any(), any())).thenReturn(Future.successful(HttpResponse(200, "")))

        val fsrc = mock[RouteChecker]
        when(fsrc.canForward(eqTo(Gb), eqTo(channel))).thenReturn(false)

        val result = service(fsrc, mc).submitMessage(input, channel, hc)

        result mustBe a[Left[FailureMessage, _]]
      }
    }

    "destination message starting with XI sends XI flag when feature switch is true" in {
      val input = <TransitWrapper>
        <CC007A>
          <CUSOFFPREOFFRES>
            <RefNumRES1>XI12345</RefNumRES1>
          </CUSOFFPREOFFRES>
        </CC007A>
      </TransitWrapper>

      forAll(channelGen) { channel =>
        val mc = mock[MessageConnector]
        when(mc.post(any(), any(), any())).thenReturn(Future.successful(HttpResponse(200, "")))

        val fsrc = mock[RouteChecker]
        when(fsrc.canForward(eqTo(Xi), eqTo(channel))).thenReturn(true)

        service(fsrc, mc).submitMessage(input, channel, hc)

        verify(mc).post(any(), eqTo(Xi), eqTo(hc))
      }
    }

    "destination message starting with XI is rejected feature switch is false" in {
      val input = <TransitWrapper>
        <CC007A>
          <CUSOFFPREOFFRES>
            <RefNumRES1>XI12345</RefNumRES1>
          </CUSOFFPREOFFRES>
        </CC007A>
      </TransitWrapper>

      forAll(channelGen) { channel =>
        val mc = mock[MessageConnector]
        when(mc.post(any(), any(), any())).thenReturn(Future.successful(HttpResponse(200, "")))

        val fsrc = mock[RouteChecker]
        when(fsrc.canForward(eqTo(Xi), eqTo(channel))).thenReturn(false)

        val result = service(fsrc, mc).submitMessage(input, channel, hc)

        result mustBe a[Left[FailureMessage, _]]
      }
    }

    "destination message starting with GB sends Gb flag when feature switch is true" in {
      val input = <TransitWrapper>
        <CC007A>
          <CUSOFFPREOFFRES>
            <RefNumRES1>GB12345</RefNumRES1>
          </CUSOFFPREOFFRES>
        </CC007A>
      </TransitWrapper>

      forAll(channelGen) { channel =>
        val mc = mock[MessageConnector]
        when(mc.post(any(), any(), any())).thenReturn(Future.successful(HttpResponse(200, "")))

        val fsrc = mock[RouteChecker]
        when(fsrc.canForward(eqTo(Gb), eqTo(channel))).thenReturn(true)

        service(fsrc, mc).submitMessage(input, channel, hc)

        verify(mc).post(any(), eqTo(Gb), eqTo(hc))
      }
    }

    "destination message starting with GB is rejected feature switch is false" in {
      val input = <TransitWrapper>
        <CC007A>
          <CUSOFFPREOFFRES>
            <RefNumRES1>GB12345</RefNumRES1>
          </CUSOFFPREOFFRES>
        </CC007A>
      </TransitWrapper>

      forAll(channelGen) { channel =>
        val mc = mock[MessageConnector]
        when(mc.post(any(), any(), any())).thenReturn(Future.successful(HttpResponse(200, "")))

        val fsrc = mock[RouteChecker]
        when(fsrc.canForward(eqTo(Gb), eqTo(channel))).thenReturn(false)

        val result = service(fsrc, mc).submitMessage(input, channel, hc)

        result mustBe a[Left[FailureMessage, _]]
      }
    }

    "guarantee messages are routed to GB" in {
      val input = <TransitWrapper>
        <CD034A>
          <SynIdeMES1>UNOC</SynIdeMES1>
          <SynVerNumMES2>3</SynVerNumMES2>
          <MesSenMES3>MDTP-GUA-22b9899e24ee48e6a18997d1</MesSenMES3>
          <MesRecMES6>NTA.GB</MesRecMES6>
          <DatOfPreMES9>20210813</DatOfPreMES9>
          <TimOfPreMES10>175101</TimOfPreMES10>
          <IntConRefMES11>deadbeefcafeba</IntConRefMES11>
          <MesIdeMES19>deadbeefcafeba</MesIdeMES19>
          <MesTypMES20>GB034A</MesTypMES20>
          <TRAPRIRC1>
            <TINRC159>GB12345678900</TINRC159>
          </TRAPRIRC1>
          <GUAREF2>
            <GuaRefNumGRNREF21>05DE3300BE0001067A001017</GuaRefNumGRNREF21>
            <GUAQUE>
              <QueIdeQUE1>2</QueIdeQUE1>
            </GUAQUE>
            <ACCDOC728>
              <AccCodCOD729>1234</AccCodCOD729>
            </ACCDOC728>
          </GUAREF2>
        </CD034A>
      </TransitWrapper>

      forAll(channelGen) { channel =>
        val mc = mock[MessageConnector]
        when(mc.post(any(), any(), any())).thenReturn(Future.successful(HttpResponse(200, "")))

        val fsrc = mock[RouteChecker]
        when(fsrc.canForward(eqTo(Gb), eqTo(channel))).thenReturn(true)

        service(fsrc, mc).submitMessage(input, channel, hc)

        verify(mc).post(any(), eqTo(Gb), eqTo(hc))
      }
    }
  }
}
