package com.thenewmotion.ocpp
package akkahttp

import javax.xml.soap.SOAPConstants.SOAP_1_2_CONTENT_TYPE
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.`Content-Type`

/**
 * @author Yaroslav Klymko
 */
object SoapPost {
  def unapply(req: HttpRequest): Either[StatusCode, HttpRequest] = req match {
    case r@HttpRequest(HttpMethods.POST,_,hs,_,_) =>
      hs.find(hasSoapMediaType).map(_ => r).toRight(StatusCodes.UnsupportedMediaType)
    case _ => Left(StatusCodes.MethodNotAllowed)
  }

  private def hasSoapMediaType(h: HttpHeader) = PartialFunction.cond(h) {
    case `Content-Type`(ContentType(mt, _)) =>
      mt.mainType+'/'+mt.subType == SOAP_1_2_CONTENT_TYPE
  }

}
