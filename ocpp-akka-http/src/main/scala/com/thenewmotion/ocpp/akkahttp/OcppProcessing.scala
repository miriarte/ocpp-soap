package com.thenewmotion.ocpp
package akkahttp

import java.net.InetAddress

import org.slf4j.LoggerFactory

import xml.{NodeSeq, XML}
import soapenvelope12.{Body, Envelope, Fault}
import com.thenewmotion.ocpp.soap.Fault._
import java.io.ByteArrayInputStream

import akka.http.scaladsl.model.{HttpRequest, HttpResponse, RemoteAddress}
import akka.http.scaladsl.model.headers.{`X-Forwarded-For`, `Remote-Address`}

import scala.language.implicitConversions
import scala.concurrent.{ExecutionContext, Future}
import com.thenewmotion.ocpp.soap._

/**
 * The information about the charge point available in an incoming request
 */
case class ChargerInfo(ocppVersion: Version, endpointUrl: Option[Uri], chargerId: String, ipAddress: Option[InetAddress])

object OcppProcessing {

  private val logger = LoggerFactory.getLogger(OcppProcessing.getClass)

  type ResponseFunc[T] = Option[T] => HttpResponse
  type ChargerId = String
  type ProcessingFunc[REQ, RES] = (ChargerInfo, REQ) => Future[RES]
  type OcppMessageLogger = (ChargerId, Version, Any) => Unit

  def apply[REQ, RES](req: HttpRequest)(f: ProcessingFunc[REQ, RES])
                     (implicit ocppService: OcppService[REQ, RES], ec: ExecutionContext): Future[HttpResponse] = {
    val (decoded, encode) = decodeEncode(req)
    applyDecoded(decoded)(f) map encode
  }

  private def inetAddress(req: HttpRequest): Option[InetAddress] = {
    val headers = req.headers
    headers.collectFirst {
      case `X-Forwarded-For`(x) if x.nonEmpty => x.head
    } match {
      case Some(RemoteAddress.IP(x))   => Some(x)
      case Some(RemoteAddress.Unknown) => None
      case None => headers.collectFirst {
        case `Remote-Address`(RemoteAddress.IP(x)) => x
      }
    }
  }

  private[spray] def applyDecoded[REQ, RES](req: HttpRequest)(f: ProcessingFunc[REQ, RES])
                                           (implicit ocppService: OcppService[REQ, RES], ec: ExecutionContext): Future[HttpResponse] = {
    val errorResponseOrFuture = safe {
      for {
        post <- soapPost(req).right
        xml <- toXml(post).right
        env <- envelope(xml).right
        chargerId <- chargerId(env).right
        version <- parseVersion(env.Body).right
        chargerInfo <- Right(ChargerInfo(
          version, ChargeBoxAddress.unapply(env), chargerId, inetAddress(req))).right
      } yield {
        httpLogger.debug(s">>\n\t${req.headers.mkString("\n\t")}\n\t$xml")
        dispatch(chargerInfo.ocppVersion, env.Body, f(chargerInfo, _: REQ)) map (OcppResponse(_))
      }
    }

    val futureResponse = errorResponseOrFuture match {
      case Right(future) => future
      case Left(response) => Future.successful(response)
    }

    futureResponse recover {
      case e: Exception => OcppResponse(InternalError(e.getMessage))
    }
  }

  private def parseVersion(body: Body): Either[HttpResponse, Version] = {
    VersionFromBody(body) match {
      case Some(version) => Right(version)
      case None => ProtocolError("Can't find an ocpp version")
    }
  }

  private def safe[T](func: => Either[HttpResponse, T]): Either[HttpResponse, T] = try func catch {
    case e: Exception =>
      logger.error(e.getMessage, e)
      errorToEither(InternalError(e.getMessage))
  }

  private def soapPost(req: HttpRequest): Either[HttpResponse, HttpRequest] =
    SoapPost.unapply(req) match {
      case Left(statusCode) => Left(HttpResponse(statusCode))
      case _ => Right(req)
    }

  private def toXml(req: HttpRequest): Either[HttpResponse, NodeSeq] = {
    try XML.load(new ByteArrayInputStream(req.entity.data.toByteArray)) match {
      case NodeSeq.Empty => ProtocolError("Body is empty")
      case xml =>
        logger.debug(">> " + xml.toString())
        Right(xml)
    } catch {
      case e: Exception =>
        logger.error(e.getMessage)
        ProtocolError(e.getMessage)
    }
  }

  private def envelope(xml: NodeSeq): Either[HttpResponse, Envelope] =
    scalaxb.fromXMLEither[Envelope](xml).left.map(x => OcppResponse(ProtocolError(x)))

  private def chargerId(env: Envelope): Either[HttpResponse, ChargerId] =
    ChargeBoxIdentity.unapply(env).toRight {
      val msg = "Failed to parse 'chargeBoxIdentity' value"
      logger.warn(msg)
      OcppResponse(ProtocolError(msg))
    }

  private[spray] def dispatch[REQ, RES](version: Version, body: Body, f: REQ => Future[RES])
                                (implicit ocppService: OcppService[REQ, RES], ec: ExecutionContext): Future[Body] =
    ocppService(version).dispatch(body, f)

  implicit def errorToEither[T](x: Fault): Either[HttpResponse, T] = Left(OcppResponse(x))

  def decodeEncode(request: HttpRequest): (HttpRequest, (HttpResponse => HttpResponse)) = {
    import _root_.spray.httpx.encoding.{Gzip, Deflate, NoEncoding}

    val encoder = request.headers.collectFirst {
      case header if header is "content-encoding" => header.value
    } match {
      case Some("deflate") => Deflate
      case Some("gzip") => Gzip
      case _ => NoEncoding
    }

    val decoded = encoder.decode(request)
    decoded -> encoder.encode[HttpResponse]
  }
}

